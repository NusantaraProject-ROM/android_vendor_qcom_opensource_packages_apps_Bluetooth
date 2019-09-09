/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.bluetooth.a2dp;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothCodecConfig;
import android.bluetooth.BluetoothCodecStatus;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothUuid;
import android.bluetooth.IBluetoothA2dp;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.HandlerThread;
import android.util.Log;
import android.util.StatsLog;

import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.SystemProperties;

import com.android.bluetooth.BluetoothMetricsProto;
import com.android.bluetooth.Utils;
import com.android.bluetooth.avrcp.Avrcp;
import com.android.bluetooth.avrcp.Avrcp_ext;
import com.android.bluetooth.avrcp.AvrcpTargetService;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.MetricsLogger;
import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.btservice.ServiceFactory;
import com.android.bluetooth.ba.BATService;
import com.android.bluetooth.gatt.GattService;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Provides Bluetooth A2DP profile, as a service in the Bluetooth application.
 * @hide
 */
public class A2dpService extends ProfileService {
    private static final boolean DBG = true;
    private static final String TAG = "A2dpService";

    private static A2dpService sA2dpService;

    private AdapterService mAdapterService;
    private HandlerThread mStateMachinesThread;
    private Avrcp mAvrcp;
    private Avrcp_ext mAvrcp_ext;
    private final Object mBtA2dpLock = new Object();
    private final Object mBtTwsLock = new Object();
    private final Object mBtAvrcpLock = new Object();
    private final Object mActiveDeviceLock = new Object();

    @VisibleForTesting
    A2dpNativeInterface mA2dpNativeInterface;
    @VisibleForTesting
    ServiceFactory mFactory = new ServiceFactory();
    private AudioManager mAudioManager;
    private A2dpCodecConfig mA2dpCodecConfig;

    @GuardedBy("mStateMachines")
    private BluetoothDevice mActiveDevice;
    private final ConcurrentMap<BluetoothDevice, A2dpStateMachine> mStateMachines =
            new ConcurrentHashMap<>();
    private static final int[] CONNECTING_CONNECTED_STATES = {
             BluetoothProfile.STATE_CONNECTING, BluetoothProfile.STATE_CONNECTED
             };

    // Upper limit of all A2DP devices: Bonded or Connected
    private static final int MAX_A2DP_STATE_MACHINES = 50;
    // Upper limit of all A2DP devices that are Connected or Connecting
    private int mMaxConnectedAudioDevices = 1;
    private int mSetMaxConnectedAudioDevices = 1;
    // A2DP Offload Enabled in platform
    boolean mA2dpOffloadEnabled = false;
    private boolean disconnectExisting = false;
    private int EVENT_TYPE_NONE = 0;
    private int mA2dpStackEvent = EVENT_TYPE_NONE;
    private BroadcastReceiver mBondStateChangedReceiver;
    private BroadcastReceiver mConnectionStateChangedReceiver;
    private boolean mIsTwsPlusEnabled = false;
    private boolean mIsTwsPlusMonoSupported = false;
    private String  mTwsPlusChannelMode = "dual-mono";
    private BluetoothDevice mDummyDevice = null;
    private static final int max_tws_connection = 2;
    private static final int min_tws_connection = 1;

    private static final int APTX_HQ = 0x1000;
    private static final int APTX_LL = 0x2000;
    private static final int APTX_ULL = 0x6000;
    private static final long APTX_MODE_MASK = 0x7000;
    private static final long APTX_SCAN_FILTER_MASK = 0x8000;

    private static final int SET_EBMONO_CFG = 1;
    private static final int SET_EBDUALMONO_CFG = 2;
    private static final int MonoCfg_Timeout = 3000;
    private static final int DualMonoCfg_Timeout = 3000;

    private Handler mHandler = new Handler() {
        @Override
       public void handleMessage(Message msg)
       {
         synchronized(mBtTwsLock) {
           switch (msg.what) {
               case SET_EBMONO_CFG:
                   Log.d(TAG, "setparameters to Mono");
                   mAudioManager.setParameters("TwsChannelConfig=mono");
                   mTwsPlusChannelMode = "mono";
                   break;
               case SET_EBDUALMONO_CFG:
                   Log.d(TAG, "setparameters to Dual-Mono");
                   mAudioManager.setParameters("TwsChannelConfig=dual-mono");
                   mTwsPlusChannelMode = "dual-mono";
                   break;
              default:
                   break;
           }
         }
       }
    };

    @Override
    protected IProfileServiceBinder initBinder() {
        return new BluetoothA2dpBinder(this);
    }

    @Override
    protected void create() {
        Log.i(TAG, "create()");
    }

    @Override
    protected boolean start() {
        Log.i(TAG, "start()");
        if (sA2dpService != null) {
            Log.w(TAG, "A2dpService is already running");
            return true;
        }

        // Step 1: Get AdapterService, A2dpNativeInterface, AudioManager.
        // None of them can be null.
        mAdapterService = Objects.requireNonNull(AdapterService.getAdapterService(),
                "AdapterService cannot be null when A2dpService starts");
        mA2dpNativeInterface = Objects.requireNonNull(A2dpNativeInterface.getInstance(),
                "A2dpNativeInterface cannot be null when A2dpService starts");
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        Objects.requireNonNull(mAudioManager,
                               "AudioManager cannot be null when A2dpService starts");

        // Step 2: Get maximum number of connected audio devices
        mMaxConnectedAudioDevices = mAdapterService.getMaxConnectedAudioDevices();
        mSetMaxConnectedAudioDevices = mMaxConnectedAudioDevices;
        Log.i(TAG, "Max connected audio devices set to " + mMaxConnectedAudioDevices);
        if (mAdapterService.isVendorIntfEnabled()) {
            String twsPlusEnabled = SystemProperties.get("persist.vendor.btstack.enable.twsplus");
            if (!twsPlusEnabled.isEmpty() && "true".equals(twsPlusEnabled)) {
                mIsTwsPlusEnabled = true;
            }
            Log.i(TAG, "mMaxConnectedAudioDevices: " + mMaxConnectedAudioDevices);
            String twsShoEnabled = SystemProperties.get("persist.vendor.btstack.enable.twsplussho");
            if (!twsShoEnabled.isEmpty() && "true".equals(twsShoEnabled) &&
                (mIsTwsPlusEnabled == true) && mMaxConnectedAudioDevices <= 2) {
                mMaxConnectedAudioDevices = 3;
                Log.i(TAG, "TWS+ SHO enabled mMaxConnectedAudioDevices changed to: " + mMaxConnectedAudioDevices);
            } else if (mIsTwsPlusEnabled && mMaxConnectedAudioDevices < 2) {
                mMaxConnectedAudioDevices = 2;
                Log.i(TAG, "TWS+ enabled mMaxConnectedAudioDevices changed to: " + mMaxConnectedAudioDevices);
            }
            mSetMaxConnectedAudioDevices = mMaxConnectedAudioDevices;
            String twsPlusMonoEnabled = SystemProperties.get("persist.vendor.btstack.twsplus.monosupport");
            if (!twsPlusMonoEnabled.isEmpty() && "true".equals(twsPlusMonoEnabled)) {
                mIsTwsPlusMonoSupported = true;
            }
            String TwsPlusChannelMode = SystemProperties.get("persist.vendor.btstack.twsplus.defaultchannelmode");
            if (!TwsPlusChannelMode.isEmpty() && "mono".equals(TwsPlusChannelMode)) {
                mTwsPlusChannelMode = "mono";
            }
            Log.d(TAG, "Default TwsPlus ChannelMode: " + mTwsPlusChannelMode);
        }

        // Step 3: Setup AVRCP
        if(mAdapterService.isVendorIntfEnabled())
            mAvrcp_ext = Avrcp_ext.make(this, this, mMaxConnectedAudioDevices);
        else
            mAvrcp = Avrcp.make(this);

        // Step 4: Start handler thread for state machines
        mStateMachines.clear();
        mStateMachinesThread = new HandlerThread("A2dpService.StateMachines");
        mStateMachinesThread.start();

        // Step 5: Setup codec config
        mA2dpCodecConfig = new A2dpCodecConfig(this, mA2dpNativeInterface);

        // Step 6: Initialize native interface
        List<BluetoothCodecConfig> mCodecConfigOffload;
        mCodecConfigOffload = mAudioManager.getHwOffloadEncodingFormatsSupportedForA2DP();
        BluetoothCodecConfig[] OffloadCodecConfig  = new BluetoothCodecConfig[mCodecConfigOffload.size()];
        OffloadCodecConfig  = mCodecConfigOffload.toArray(OffloadCodecConfig);
        mA2dpNativeInterface.init(mMaxConnectedAudioDevices,
                           mA2dpCodecConfig.codecConfigPriorities(),OffloadCodecConfig);

        // Step 7: Check if A2DP is in offload mode
        mA2dpOffloadEnabled = mAdapterService.isA2dpOffloadEnabled();
        if (DBG) {
            Log.d(TAG, "A2DP offload flag set to " + mA2dpOffloadEnabled);
        }

        // Step 8: Setup broadcast receivers
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        mBondStateChangedReceiver = new BondStateChangedReceiver();
        registerReceiver(mBondStateChangedReceiver, filter);
        filter = new IntentFilter();
        filter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        mConnectionStateChangedReceiver = new ConnectionStateChangedReceiver();
        registerReceiver(mConnectionStateChangedReceiver, filter);

        // Step 9: Mark service as started
        setA2dpService(this);

        // Step 10: Clear active device
        setActiveDevice(null);

        return true;
    }

    @Override
    protected boolean stop() {
        Log.i(TAG, "stop()");
        if (sA2dpService == null) {
            Log.w(TAG, "stop() called before start()");
            return true;
        }

        // Step 9: Clear active device and stop playing audio
        removeActiveDevice(true);

        // Step 8: Mark service as stopped
        setA2dpService(null);

        // Step 7: Unregister broadcast receivers
        unregisterReceiver(mConnectionStateChangedReceiver);
        mConnectionStateChangedReceiver = null;
        unregisterReceiver(mBondStateChangedReceiver);
        mBondStateChangedReceiver = null;

        // Step 6: Cleanup native interface
        mA2dpNativeInterface.cleanup();
        mA2dpNativeInterface = null;

        // Step 5: Clear codec config
        mA2dpCodecConfig = null;

        // Step 4: Destroy state machines and stop handler thread
        synchronized (mBtA2dpLock) {
            for (A2dpStateMachine sm : mStateMachines.values()) {
                sm.doQuit();
                sm.cleanup();
            }
            mStateMachines.clear();
        }
        mStateMachinesThread.quitSafely();
        mStateMachinesThread = null;

        // Step 3: Cleanup AVRCP
        synchronized (mBtAvrcpLock) {
            if(mAvrcp_ext != null) {
                mAvrcp_ext.doQuit();
                mAvrcp_ext.cleanup();
                Avrcp_ext.clearAvrcpInstance();
                mAvrcp_ext = null;
            } else if(mAvrcp != null) {
                mAvrcp.doQuit();
                mAvrcp.cleanup();
                mAvrcp = null;
            }
        }

        // Step 2: Reset maximum number of connected audio devices
        if (mAdapterService.isVendorIntfEnabled()) {
            if (mIsTwsPlusEnabled) {
                mMaxConnectedAudioDevices = 2;
            } else {
               mMaxConnectedAudioDevices = 1;
            }
        } else {
            mMaxConnectedAudioDevices = 1;
        }
        mSetMaxConnectedAudioDevices = 1;

        // Step 1: Clear AdapterService, A2dpNativeInterface, AudioManager
        mAudioManager = null;
        mA2dpNativeInterface = null;
        mAdapterService = null;

        return true;
    }

    @Override
    protected void cleanup() {
        Log.i(TAG, "cleanup()");
    }

    public static synchronized A2dpService getA2dpService() {
        if (sA2dpService == null) {
            Log.w(TAG, "getA2dpService(): service is null");
            return null;
        }
        if (!sA2dpService.isAvailable()) {
            Log.w(TAG, "getA2dpService(): service is not available");
            return null;
        }
        return sA2dpService;
    }

    private static synchronized void setA2dpService(A2dpService instance) {
        if (DBG) {
            Log.d(TAG, "setA2dpService(): set to: " + instance);
        }
        sA2dpService = instance;
    }

    public boolean connect(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH ADMIN permission");
        if (DBG) {
            Log.d(TAG, "connect(): " + device);
        }

        if (getPriority(device) == BluetoothProfile.PRIORITY_OFF) {
            Log.e(TAG, "Cannot connect to " + device + " : PRIORITY_OFF");
            return false;
        }
        if (!BluetoothUuid.isUuidPresent(mAdapterService.getRemoteUuids(device),
                                         BluetoothUuid.AudioSink)) {
            Log.e(TAG, "Cannot connect to " + device + " : Remote does not have A2DP Sink UUID");
            return false;
        }

        synchronized (mBtA2dpLock) {
            if (!connectionAllowedCheckMaxDevices(device)) {
                // when mMaxConnectedAudioDevices is one, disconnect current device first.
                if (mMaxConnectedAudioDevices == 1) {
                    List<BluetoothDevice> sinks = getDevicesMatchingConnectionStates(
                            new int[] {BluetoothProfile.STATE_CONNECTED,
                                    BluetoothProfile.STATE_CONNECTING,
                                    BluetoothProfile.STATE_DISCONNECTING});
                    for (BluetoothDevice sink : sinks) {
                        if (sink.equals(device)) {
                            Log.w(TAG, "Connecting to device " + device + " : disconnect skipped");
                            continue;
                        }
                        disconnect(sink);
                    }
                } else {
                    Log.e(TAG, "Cannot connect to " + device + " : too many connected devices");
                    return false;
                }
            }
            if (disconnectExisting) {
                disconnectExisting = false;
                //Log.e(TAG,"Disconnect existing connections");
                List <BluetoothDevice> connectingConnectedDevices =
                      getDevicesMatchingConnectionStates(CONNECTING_CONNECTED_STATES);
                Log.e(TAG,"Disconnect existing connections = " + connectingConnectedDevices.size());
                for (BluetoothDevice connectingConnectedDevice : connectingConnectedDevices) {
                    Log.d(TAG,"calling disconnect to " + connectingConnectedDevice);
                    disconnect(connectingConnectedDevice);
                }
            }
            A2dpStateMachine smConnect = getOrCreateStateMachine(device);
            if (smConnect == null) {
                Log.e(TAG, "Cannot connect to " + device + " : no state machine");
                return false;
            }
            smConnect.sendMessage(A2dpStateMachine.CONNECT);
            return true;
        }
    }

    boolean disconnect(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH ADMIN permission");
        if (DBG) {
            Log.d(TAG, "disconnect(): " + device);
        }

        synchronized (mBtA2dpLock) {
            A2dpStateMachine sm = mStateMachines.get(device);
            if (sm == null) {
                Log.e(TAG, "Ignored disconnect request for " + device + " : no state machine");
                return false;
            }
            sm.sendMessage(A2dpStateMachine.DISCONNECT);
            return true;
        }
    }

    public List<BluetoothDevice> getConnectedDevices() {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        synchronized (mBtA2dpLock) {
            List<BluetoothDevice> devices = new ArrayList<>();
            for (A2dpStateMachine sm : mStateMachines.values()) {
                if (sm.isConnected()) {
                    devices.add(sm.getDevice());
                }
            }
            return devices;
        }
    }
    private boolean isConnectionAllowed(BluetoothDevice device, int tws_connected,
                                        int num_connected) {
        if (!mIsTwsPlusEnabled && mAdapterService.isTwsPlusDevice(device)) {
           Log.d(TAG, "No TWSPLUS connections as It is not Enabled");
           return false;
        }
        if (num_connected == 0) return true;
        Log.d(TAG,"isConnectionAllowed");
        List <BluetoothDevice> connectingConnectedDevices =
                  getDevicesMatchingConnectionStates(CONNECTING_CONNECTED_STATES);
        BluetoothDevice mConnDev = null;
        if (mMaxConnectedAudioDevices > 2 && tws_connected > 0) {
            for (BluetoothDevice connectingConnectedDevice : connectingConnectedDevices) {
                if (mAdapterService.isTwsPlusDevice(connectingConnectedDevice)) {
                    mConnDev = connectingConnectedDevice;
                    break;
                }
            }
        } else if (!connectingConnectedDevices.isEmpty()) {
            mConnDev = connectingConnectedDevices.get(0);
        }
        if (mA2dpStackEvent == A2dpStackEvent.CONNECTION_STATE_CONNECTING ||
            mA2dpStackEvent == A2dpStackEvent.CONNECTION_STATE_CONNECTED) {
            //Handle incoming connection
            if (!mAdapterService.isTwsPlusDevice(device) &&
                ((mMaxConnectedAudioDevices - max_tws_connection) - (num_connected - tws_connected)) < 1) {
                Log.d(TAG,"isConnectionAllowed: incoming connection not allowed");
                mA2dpStackEvent = EVENT_TYPE_NONE;
                return false;
            }
        }
        if (mAdapterService.isTwsPlusDevice(device)) {
            if (tws_connected == max_tws_connection) {
                Log.d(TAG,"isConnectionAllowed:TWS+ pair connected, disallow other TWS+ connection");
                return false;
            }
            if ((tws_connected > 0 && (mMaxConnectedAudioDevices - num_connected) >= min_tws_connection) ||
                (tws_connected == 0 && (mMaxConnectedAudioDevices - num_connected) >= max_tws_connection)){
                if ((tws_connected == 0) || (tws_connected == min_tws_connection && mConnDev != null &&
                    mAdapterService.getTwsPlusPeerAddress(mConnDev).equals(device.getAddress()))) {
                    Log.d(TAG,"isConnectionAllowed: Allow TWS+ connection");
                    return true;
                }
            } else {
                Log.d(TAG,"isConnectionAllowed: Too many connections, TWS+ connection not allowed");
                return false;
            }
        } else {
            if ((tws_connected == max_tws_connection && (mMaxConnectedAudioDevices - num_connected) >= 1) ||
                (tws_connected == min_tws_connection && (mMaxConnectedAudioDevices - num_connected) >= 2)) {
                Log.d(TAG,"isConnectionAllowed: Allow legacy connection");
                return true;
            } else {
                Log.d(TAG,"isConnectionAllowed: Too many connections, legacy connection not allowed");
                return false;
            }
        }
        return false;
    }
    /**
     * Check whether can connect to a peer device.
     * The check considers the maximum number of connected peers.
     *
     * @param device the peer device to connect to
     * @return true if connection is allowed, otherwise false
     */
    private boolean connectionAllowedCheckMaxDevices(BluetoothDevice device) {
        int connected = 0;
        int tws_device = 0;
        // Count devices that are in the process of connecting or already connected
        synchronized (mBtA2dpLock) {
            for (A2dpStateMachine sm : mStateMachines.values()) {
                switch (sm.getConnectionState()) {
                    case BluetoothProfile.STATE_CONNECTING:
                    case BluetoothProfile.STATE_CONNECTED:
                        if (Objects.equals(device, sm.getDevice())) {
                            return true;    // Already connected or accounted for
                        }
                        if (mAdapterService.isTwsPlusDevice(sm.getDevice()))
                            tws_device++;
                        connected++;
                        break;
                    default:
                        break;
                }
            }
        }
        Log.d(TAG,"connectionAllowedCheckMaxDevices connected = " + connected +
              "tws connected = " + tws_device);
        if (mAdapterService.isVendorIntfEnabled() &&
            ((tws_device > 0) || mAdapterService.isTwsPlusDevice(device) ||
            ((tws_device > 0) && connected == mMaxConnectedAudioDevices &&
            !mAdapterService.isTwsPlusDevice(device)))) {
            return isConnectionAllowed(device, tws_device, connected);
        }
        if (mSetMaxConnectedAudioDevices == 1 &&
            connected == mSetMaxConnectedAudioDevices) {
            disconnectExisting = true;
            return true;
        }
        return (connected < mSetMaxConnectedAudioDevices);
    }

    /**
     * Check whether can connect to a peer device.
     * The check considers a number of factors during the evaluation.
     *
     * @param device the peer device to connect to
     * @param isOutgoingRequest if true, the check is for outgoing connection
     * request, otherwise is for incoming connection request
     * @return true if connection is allowed, otherwise false
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public boolean okToConnect(BluetoothDevice device, boolean isOutgoingRequest) {
        Log.i(TAG, "okToConnect: device " + device + " isOutgoingRequest: " + isOutgoingRequest);
        // Check if this is an incoming connection in Quiet mode.
        if (mAdapterService.isQuietModeEnabled() && !isOutgoingRequest) {
            Log.e(TAG, "okToConnect: cannot connect to " + device + " : quiet mode enabled");
            return false;
        }
        // Check if too many devices
        if (!connectionAllowedCheckMaxDevices(device)) {
            Log.e(TAG, "okToConnect: cannot connect to " + device
                    + " : too many connected devices");
            return false;
        }

        // Check priority and accept or reject the connection.
        // Note: Logic can be simplified, but keeping it this way for readability
        int priority = getPriority(device);
        int bondState = mAdapterService.getBondState(device);
        // If priority is undefined, it is likely that service discovery has not completed and peer
        // initiated the connection. Allow this connection only if the device is bonded or bonding
        boolean serviceDiscoveryPending = (priority == BluetoothProfile.PRIORITY_UNDEFINED)
                && (bondState == BluetoothDevice.BOND_BONDING
                || bondState == BluetoothDevice.BOND_BONDED);
        // Also allow connection when device is bonded/bonding and priority is ON/AUTO_CONNECT.
        boolean isEnabled = (priority == BluetoothProfile.PRIORITY_ON
                || priority == BluetoothProfile.PRIORITY_AUTO_CONNECT)
                && (bondState == BluetoothDevice.BOND_BONDED
                || bondState == BluetoothDevice.BOND_BONDING);
        if (!serviceDiscoveryPending && !isEnabled) {
            // Otherwise, reject the connection if no service discovery is pending and priority is
            // neither PRIORITY_ON nor PRIORITY_AUTO_CONNECT
            Log.w(TAG, "okToConnect: return false, priority=" + priority + ", bondState="
                    + bondState);
            return false;
        }
        return true;
    }

    List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        List<BluetoothDevice> devices = new ArrayList<>();
        if (states == null) {
            return devices;
        }
        final BluetoothDevice[] bondedDevices = mAdapterService.getBondedDevices();
        if (bondedDevices == null) {
            return devices;
        }
        synchronized (mStateMachines) {
            for (BluetoothDevice device : bondedDevices) {
                if (!BluetoothUuid.isUuidPresent(mAdapterService.getRemoteUuids(device),
                                                 BluetoothUuid.AudioSink)) {
                    continue;
                }
                int connectionState = BluetoothProfile.STATE_DISCONNECTED;
                A2dpStateMachine sm = mStateMachines.get(device);
                if (sm != null) {
                    connectionState = sm.getConnectionState();
                }
                for (int state : states) {
                    if (connectionState == state) {
                        devices.add(device);
                        break;
                    }
                }
            }
            return devices;
        }
    }

    /**
     * Get the list of devices that have state machines.
     *
     * @return the list of devices that have state machines
     */
    @VisibleForTesting
    List<BluetoothDevice> getDevices() {
        List<BluetoothDevice> devices = new ArrayList<>();
        synchronized (mBtA2dpLock) {
            for (A2dpStateMachine sm : mStateMachines.values()) {
                devices.add(sm.getDevice());
            }
            return devices;
        }
    }

    public int getConnectionState(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        synchronized (mBtA2dpLock) {
            A2dpStateMachine sm = mStateMachines.get(device);
            if (sm == null) {
                return BluetoothProfile.STATE_DISCONNECTED;
            }
            return sm.getConnectionState();
        }
    }

    private void storeActiveDeviceVolume() {
        // Make sure volume has been stored before been removed from active.
        if (mFactory.getAvrcpTargetService() != null && mActiveDevice != null) {
            mFactory.getAvrcpTargetService().storeVolumeForDevice(mActiveDevice);
        }
        if (mActiveDevice != null && mAvrcp_ext != null) {
            mAvrcp_ext.storeVolumeForDevice(mActiveDevice);
        }
    }

    private void removeActiveDevice(boolean forceStopPlayingAudio) {
        BluetoothDevice previousActiveDevice = mActiveDevice;
        synchronized (mBtA2dpLock) {
            // Make sure volume has been store before device been remove from active.
            storeActiveDeviceVolume();

            // This needs to happen before we inform the audio manager that the device
            // disconnected. Please see comment in updateAndBroadcastActiveDevice() for why.
            updateAndBroadcastActiveDevice(null);

            if (previousActiveDevice == null) {
                return;
            }

            // Make sure the Audio Manager knows the previous Active device is disconnected.
            // However, if A2DP is still connected and not forcing stop audio for that remote
            // device, the user has explicitly switched the output to the local device and music
            // should continue playing. Otherwise, the remote device has been indeed disconnected
            // and audio should be suspended before switching the output to the local device.
            boolean suppressNoisyIntent = !forceStopPlayingAudio
                    && (getConnectionState(previousActiveDevice)
                    == BluetoothProfile.STATE_CONNECTED);
            Log.i(TAG, "removeActiveDevice: suppressNoisyIntent=" + suppressNoisyIntent);

            boolean isBAActive = false;
            BATService mBatService = BATService.getBATService();
            isBAActive = (mBatService != null) && (mBatService.isBATActive());
            Log.d(TAG," removeActiveDevice: BA active " + isBAActive);
            // If BA streaming is ongoing, we don't want to pause music player
            if(!isBAActive) {
                mAudioManager.handleBluetoothA2dpActiveDeviceChange(
                        previousActiveDevice, BluetoothProfile.STATE_DISCONNECTED,
                        BluetoothProfile.A2DP, suppressNoisyIntent, -1);
            }
        }
        // Make sure the Active device in native layer is set to null and audio is off
        if (!mA2dpNativeInterface.setActiveDevice(null)) {
            Log.w(TAG, "setActiveDevice(null): Cannot remove active device in native "
                    + "layer");
        }
    }

    /**
     * Process a change in the silence mode for a {@link BluetoothDevice}.
     *
     * @param device the device to change silence mode
     * @param silence true to enable silence mode, false to disable.
     * @return true on success, false on error
     */
    @VisibleForTesting
    public boolean setSilenceMode(BluetoothDevice device, boolean silence) {
        if (DBG) {
            Log.d(TAG, "setSilenceMode(" + device + "): " + silence);
        }
        if (silence && Objects.equals(mActiveDevice, device)) {
            removeActiveDevice(true);
        } else if (!silence && mActiveDevice == null) {
            // Set the device as the active device if currently no active device.
            setActiveDevice(device);
        }
        if (!mA2dpNativeInterface.setSilenceDevice(device, silence)) {
            Log.e(TAG, "Cannot set " + device + " silence mode " + silence + " in native layer");
            return false;
        }
        return true;
    }

    /**
     * Early notification that Hearing Aids will be the active device. This allows the A2DP to save
     * its volume before the Audio Service starts changing its media stream.
     */
    public void earlyNotifyHearingAidActive() {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH ADMIN permission");

        synchronized (mStateMachines) {
            // Switch active device from A2DP to Hearing Aids.
            if (DBG) {
                Log.d(TAG, "earlyNotifyHearingAidActive: Save volume for " + mActiveDevice);
            }
            storeActiveDeviceVolume();
        }
    }

    /**
     * Set the active device.
     *
     * @param device the active device
     * @return true on success, otherwise false
     */
    public boolean setActiveDevice(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH ADMIN permission");

        if(mAvrcp_ext != null) {
            return mAvrcp_ext.startSHO(device, false);
        }

        return false;
    }

    public boolean startSHO(BluetoothDevice device) {
        synchronized (mActiveDeviceLock) {
            return setActiveDeviceInternal(device);
        }
    }

    private boolean setActiveDeviceInternal(BluetoothDevice device) {
        boolean deviceChanged;
        BluetoothCodecStatus codecStatus = null;
        BluetoothDevice previousActiveDevice = mActiveDevice;
        boolean isBAActive = false;
        boolean tws_switch = false;
        Log.w(TAG, "setActiveDevice(" + device + "): previous is " + previousActiveDevice);

        synchronized (mBtA2dpLock) {
            BATService mBatService = BATService.getBATService();
            isBAActive = (mBatService != null) && (mBatService.isBATActive());
            Log.d(TAG," setActiveDevice: BA active " + isBAActive);

            if (device == null) {
                // Remove active device and continue playing audio only if necessary.
                removeActiveDevice(false);
                if(mAvrcp_ext != null)
                    mAvrcp_ext.setActiveDevice(device);
                return true;
            }

            A2dpStateMachine sm = mStateMachines.get(device);
            deviceChanged = !Objects.equals(device, mActiveDevice);
            if (!deviceChanged) {
                Log.e(TAG, "setActiveDevice(" + device + "): already set to active ");
                return true;
            }
            if (sm == null) {
                Log.e(TAG, "setActiveDevice(" + device + "): Cannot set as active: "
                          + "no state machine");
                return false;
            }
            if (sm.getConnectionState() != BluetoothProfile.STATE_CONNECTED) {
                Log.e(TAG, "setActiveDevice(" + device + "): Cannot set as active: "
                          + "device is not connected");
                return false;
            }
            if (mActiveDevice != null && mAdapterService.isTwsPlusDevice(device) &&
                mAdapterService.isTwsPlusDevice(mActiveDevice) &&
                !Objects.equals(device, mActiveDevice) &&
                getConnectionState(mActiveDevice) == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG,"Ignore setActiveDevice request");
                return false;
            }
            codecStatus = sm.getCodecStatus();

            if (deviceChanged) {
                // Switch from one A2DP to another A2DP device
                if (DBG) {
                    Log.d(TAG, "Switch A2DP devices to " + device + " from " + mActiveDevice);
                }
                storeActiveDeviceVolume();
            }

            // This needs to happen before we inform the audio manager that the device
            // disconnected. Please see comment in updateAndBroadcastActiveDevice() for why.
            Log.w(TAG, "setActiveDevice coming out of mutex lock");
        }

        if (!mA2dpNativeInterface.setActiveDevice(device)) {
            Log.e(TAG, "setActiveDevice(" + device + "): Cannot set as active in native layer");
            return false;
        }
        updateAndBroadcastActiveDevice(device);
        Log.d(TAG, "setActiveDevice(" + device + "): completed");

        if (deviceChanged) {
            if(mAvrcp_ext != null)
                mAvrcp_ext.setActiveDevice(device);
            if (mAdapterService.isTwsPlusDevice(device) &&
                (previousActiveDevice != null && mAdapterService.isTwsPlusDevice(previousActiveDevice))) {
                Log.d(TAG,"TWS+ active device disconnected, setting other pair as active");
                tws_switch = true;
            }
            // Send an intent with the active device codec config
            if (codecStatus != null) {
                broadcastCodecConfig(mActiveDevice, codecStatus);
            }
            int rememberedVolume = -1;
            if (mFactory.getAvrcpTargetService() != null) {
                rememberedVolume = mFactory.getAvrcpTargetService()
                        .getRememberedVolumeForDevice(mActiveDevice);
            } else if (mAdapterService.isVendorIntfEnabled()) {
                rememberedVolume = mAvrcp_ext.getVolume(device);
                Log.d(TAG,"volume = " + rememberedVolume);
            }
            // Make sure the Audio Manager knows the previous Active device is disconnected,
            // and the new Active device is connected.
            // Also, mute and unmute the output during the switch to avoid audio glitches.
            boolean wasMuted = false;
            if (previousActiveDevice != null && !tws_switch) {
                if (!mAudioManager.isStreamMute(AudioManager.STREAM_MUSIC)) {
                   mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                                                 AudioManager.ADJUST_MUTE,
                                                 mAudioManager.FLAG_BLUETOOTH_ABS_VOLUME);
                   wasMuted = true;
                }
            }

            if (!isBAActive) {
                // Make sure the Audio Manager knows the previous
                // Active device is disconnected, and the new Active
                // device is connected.
                // Also, provide information about codec used by
                // new active device so that Audio Service
                // can reset accordingly the audio feeding parameters
                // in the Audio HAL to the Bluetooth stack.
                 mAudioManager.handleBluetoothA2dpActiveDeviceChange(
                          mActiveDevice, BluetoothProfile.STATE_CONNECTED, BluetoothProfile.A2DP,
                          true, rememberedVolume);
            }

            // Inform the Audio Service about the codec configuration
            // change, so the Audio Service can reset accordingly the audio
            // feeding parameters in the Audio HAL to the Bluetooth stack.

            // Split A2dp will be enabled by default
            boolean isSplitA2dpEnabled = true;
            AdapterService adapterService = AdapterService.getAdapterService();

            if (adapterService != null){
                isSplitA2dpEnabled = adapterService.isSplitA2dpEnabled();
                Log.v(TAG,"isSplitA2dpEnabled: " + isSplitA2dpEnabled);
            } else {
                Log.e(TAG,"adapterService is null");
            }


            if (wasMuted) {
               mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                                          AudioManager.ADJUST_UNMUTE,
                                          mAudioManager.FLAG_BLUETOOTH_ABS_VOLUME);
            }
            if (mAvrcp_ext != null && !tws_switch) {
                mAvrcp_ext.setAbsVolumeFlag(device);
            }
            tws_switch = false;
        }
        return true;
    }

    /**
     * Get the active device.
     *
     * @return the active device or null if no device is active
     */
    public BluetoothDevice getActiveDevice() {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        synchronized (mBtA2dpLock) {
            return mActiveDevice;
        }
    }

    private boolean isActiveDevice(BluetoothDevice device) {
        synchronized (mBtA2dpLock) {
            return (device != null) && Objects.equals(device, mActiveDevice);
        }
    }

    public boolean setPriority(BluetoothDevice device, int priority) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH_ADMIN permission");
        if (DBG) {
            Log.d(TAG, "Saved priority " + device + " = " + priority);
        }
        mAdapterService.getDatabase()
                .setProfilePriority(device, BluetoothProfile.A2DP, priority);
        return true;
    }

    public int getPriority(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH_ADMIN permission");
        return mAdapterService.getDatabase()
                .getProfilePriority(device, BluetoothProfile.A2DP);
    }

    /* Absolute volume implementation */
    public boolean isAvrcpAbsoluteVolumeSupported() {
        synchronized(mBtAvrcpLock) {
            if (mAvrcp_ext != null) return mAvrcp_ext.isAbsoluteVolumeSupported();
            return (mAvrcp != null) && mAvrcp.isAbsoluteVolumeSupported();
        }
    }

    public void setAvrcpAbsoluteVolume(int volume) {
        // TODO (apanicke): Instead of using A2DP as a middleman for volume changes, add a binder
        // service to the new AVRCP Profile and have the audio manager use that instead.
        if (mFactory.getAvrcpTargetService() != null) {
            mFactory.getAvrcpTargetService().sendVolumeChanged(volume);
            return;
        }

        synchronized(mBtAvrcpLock) {
            if (mAvrcp_ext != null) {
                mAvrcp_ext.setAbsoluteVolume(volume);
                return;
            }
            if (mAvrcp != null) {
                mAvrcp.setAbsoluteVolume(volume);
            }
        }
    }

    public void setAvrcpAudioState(int state, BluetoothDevice device) {
        synchronized(mBtAvrcpLock) {
            if (mAvrcp_ext != null) {
                mAvrcp_ext.setA2dpAudioState(state, device);
            } else if (mAvrcp != null) {
                mAvrcp.setA2dpAudioState(state);
            }
        }

        if(state == BluetoothA2dp.STATE_NOT_PLAYING) {
            GattService mGattService = GattService.getGattService();
            if(mGattService != null) {
                Log.d(TAG, "Enable BLE scanning");
                mGattService.setAptXLowLatencyMode(false);
            }
        }
    }

    public void resetAvrcpBlacklist(BluetoothDevice device) {
        synchronized(mBtAvrcpLock) {
            if (mAvrcp_ext != null) {
                mAvrcp_ext.resetBlackList(device.getAddress());
                return;
            }
            if (mAvrcp != null) {
                mAvrcp.resetBlackList(device.getAddress());
            }
        }
    }

    public boolean isA2dpPlaying(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        if (DBG) {
            Log.d(TAG, "isA2dpPlaying(" + device + ")");
        }
        synchronized (mBtA2dpLock) {
            A2dpStateMachine sm = mStateMachines.get(device);
            if (sm == null) {
                return false;
            }
            return sm.isPlaying();
        }
    }

    private BluetoothCodecStatus getTwsPlusCodecStatus(BluetoothCodecStatus mCodecStatus) {
        BluetoothCodecConfig mCodecConfig = mCodecStatus.getCodecConfig();
        BluetoothCodecConfig mNewCodecConfig;
        Log.d(TAG, "Return TWS codec status with " + mCodecConfig.getCodecName() + " codec");
        if(mCodecConfig.getCodecType() == BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX_ADAPTIVE) {
            mNewCodecConfig = new BluetoothCodecConfig(
                        BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX_ADAPTIVE,
                        BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT,
                        BluetoothCodecConfig.SAMPLE_RATE_48000,
                        BluetoothCodecConfig.BITS_PER_SAMPLE_NONE,
                        BluetoothCodecConfig.CHANNEL_MODE_STEREO,
                        0, 0, 0, 0);
        } else {
            mNewCodecConfig = new BluetoothCodecConfig(
                        BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX,
                        mCodecConfig.getCodecPriority(), mCodecConfig.getSampleRate(),
                        mCodecConfig.getBitsPerSample(), mCodecConfig.getChannelMode(),
                        mCodecConfig.getCodecSpecific1(), mCodecConfig.getCodecSpecific2(),
                        mCodecConfig.getCodecSpecific3(), mCodecConfig.getCodecSpecific4());
        }
        return (new BluetoothCodecStatus(mNewCodecConfig, mCodecStatus.getCodecsLocalCapabilities(),
                          mCodecStatus.getCodecsSelectableCapabilities()));
    }

    private BluetoothCodecStatus getBACodecStatus() {
        BluetoothCodecConfig mNewCodecConfig =
                    new BluetoothCodecConfig(
                        BluetoothCodecConfig.SOURCE_CODEC_TYPE_CELT,
                        BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT,
                        BluetoothCodecConfig.SAMPLE_RATE_48000,
                        BluetoothCodecConfig.BITS_PER_SAMPLE_NONE,
                        BluetoothCodecConfig.CHANNEL_MODE_STEREO,
                        0, 0, 0, 0);
        BluetoothCodecConfig[] mBACodecConfig = {mNewCodecConfig};
        return (new BluetoothCodecStatus(mNewCodecConfig, mBACodecConfig, mBACodecConfig));
    }

    /**
     * Gets the current codec status (configuration and capability).
     *
     * @param device the remote Bluetooth device. If null, use the currect
     * active A2DP Bluetooth device.
     * @return the current codec status
     * @hide
     */
    public BluetoothCodecStatus getCodecStatus(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        if (DBG) {
            Log.d(TAG, "getCodecStatus(" + device + ")");
        }
        A2dpStateMachine sm = null;
        boolean isBAActive = false;
        BATService mBatService = BATService.getBATService();
        isBAActive = (mBatService != null) && (mBatService.isBATActive());
        synchronized (mBtA2dpLock) {
            if (device == null) {
                device = mActiveDevice;
            }
            if (device == null) {
                return null;
            }
            if(isBAActive) {
                Log.d(TAG, "getBACodecStatus(" + device + ")");
                return getBACodecStatus();
            }
            if (Objects.equals(device, mDummyDevice)) {
                sm = mStateMachines.get(mActiveDevice);
                if (sm != null) {
                    return getTwsPlusCodecStatus(sm.getCodecStatus());
                }
            }
            sm = mStateMachines.get(device);
            if (sm != null) {
                return sm.getCodecStatus();
            }
            return null;
        }
    }

    /**
     * Sets the codec configuration preference.
     *
     * @param device the remote Bluetooth device. If null, use the currect
     * active A2DP Bluetooth device.
     * @param codecConfig the codec configuration preference
     * @hide
     */
    public void setCodecConfigPreference(BluetoothDevice device,
                                         BluetoothCodecConfig codecConfig) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        if (DBG) {
            Log.d(TAG, "setCodecConfigPreference(" + device + "): "
                    + Objects.toString(codecConfig));
        }
        if (device == null) {
            device = mActiveDevice;
        }
        if (device == null) {
            Log.e(TAG, "Cannot set codec config preference: no active A2DP device");
            return;
        }

        long cs4 = codecConfig.getCodecSpecific4();
            GattService mGattService = GattService.getGattService();

        if(cs4 > 0 && mGattService != null) {
            switch((int)(cs4 & APTX_MODE_MASK)) {
                case APTX_HQ:
                  mGattService.setAptXLowLatencyMode(false);
                  break;

                case APTX_LL:
                case APTX_ULL:
                  if((cs4 & APTX_SCAN_FILTER_MASK) == APTX_SCAN_FILTER_MASK) {
                    mGattService.setAptXLowLatencyMode(true);
                  } else {
                    mGattService.setAptXLowLatencyMode(false);
                  }
                  break;
                default:
                  Log.e(TAG, cs4 + " is not a aptX profile mode feedback");
            }
        }

        if (getSupportsOptionalCodecs(device) != BluetoothA2dp.OPTIONAL_CODECS_SUPPORTED) {
            Log.e(TAG, "Cannot set codec config preference: not supported");
            return;
        }

        BluetoothCodecStatus codecStatus = getCodecStatus(device);
        if (codecStatus == null) {
            Log.e(TAG, "Codec status is null on " + device);
            return;
        }
        if (mAdapterService.isTwsPlusDevice(device) && ( cs4 == 0 ||
             codecConfig.getCodecType() != BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX_ADAPTIVE )) {
            Log.w(TAG, "Block un-supportive codec on TWS+ device: " + device);
            return;
        }
        mA2dpCodecConfig.setCodecConfigPreference(device, codecStatus, codecConfig);
    }

    /**
     * Enables the optional codecs.
     *
     * @param device the remote Bluetooth device. If null, use the currect
     * active A2DP Bluetooth device.
     * @hide
     */
    public void enableOptionalCodecs(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        if (DBG) {
            Log.d(TAG, "enableOptionalCodecs(" + device + ")");
        }
        if (device == null) {
            device = mActiveDevice;
        }
        if (device == null) {
            Log.e(TAG, "Cannot enable optional codecs: no active A2DP device");
            return;
        }
        if (getSupportsOptionalCodecs(device) != BluetoothA2dp.OPTIONAL_CODECS_SUPPORTED) {
            Log.e(TAG, "Cannot enable optional codecs: not supported");
            return;
        }
        BluetoothCodecStatus codecStatus = getCodecStatus(device);
        if (codecStatus == null) {
            Log.e(TAG, "Cannot enable optional codecs: codec status is null");
            return;
        }
        mA2dpCodecConfig.enableOptionalCodecs(device, codecStatus.getCodecConfig());
    }

    /**
     * Disables the optional codecs.
     *
     * @param device the remote Bluetooth device. If null, use the currect
     * active A2DP Bluetooth device.
     * @hide
     */
    public void disableOptionalCodecs(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        if (DBG) {
            Log.d(TAG, "disableOptionalCodecs(" + device + ")");
        }
        if (device == null) {
            device = mActiveDevice;
        }
        if (device == null) {
            Log.e(TAG, "Cannot disable optional codecs: no active A2DP device");
            return;
        }
        if (getSupportsOptionalCodecs(device) != BluetoothA2dp.OPTIONAL_CODECS_SUPPORTED) {
            Log.e(TAG, "Cannot disable optional codecs: not supported");
            return;
        }
        BluetoothCodecStatus codecStatus = getCodecStatus(device);
        if (codecStatus == null) {
            Log.e(TAG, "Cannot disable optional codecs: codec status is null");
            return;
        }
        mA2dpCodecConfig.disableOptionalCodecs(device, codecStatus.getCodecConfig());
    }

    public int getSupportsOptionalCodecs(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH_ADMIN permission");
        return mAdapterService.getDatabase().getA2dpSupportsOptionalCodecs(device);
    }

    public void setSupportsOptionalCodecs(BluetoothDevice device, boolean doesSupport) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH_ADMIN permission");
        int value = doesSupport ? BluetoothA2dp.OPTIONAL_CODECS_SUPPORTED
                : BluetoothA2dp.OPTIONAL_CODECS_NOT_SUPPORTED;
        mAdapterService.getDatabase().setA2dpSupportsOptionalCodecs(device, value);
    }

    public int getOptionalCodecsEnabled(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH_ADMIN permission");
        return mAdapterService.getDatabase().getA2dpOptionalCodecsEnabled(device);
    }

    public void setOptionalCodecsEnabled(BluetoothDevice device, int value) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH_ADMIN permission");
        if (value != BluetoothA2dp.OPTIONAL_CODECS_PREF_UNKNOWN
                && value != BluetoothA2dp.OPTIONAL_CODECS_PREF_DISABLED
                && value != BluetoothA2dp.OPTIONAL_CODECS_PREF_ENABLED) {
            Log.w(TAG, "Unexpected value passed to setOptionalCodecsEnabled:" + value);
            return;
        }
        mAdapterService.getDatabase().setA2dpOptionalCodecsEnabled(device, value);
    }

    // Handle messages from native (JNI) to Java
    void messageFromNative(A2dpStackEvent stackEvent) {
        Objects.requireNonNull(stackEvent.device,
                               "Device should never be null, event: " + stackEvent);
        synchronized (mBtA2dpLock) {
            BluetoothDevice device = stackEvent.device;
            A2dpStateMachine sm = mStateMachines.get(device);
            if (sm == null) {
                if (stackEvent.type == A2dpStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED) {
                    switch (stackEvent.valueInt) {
                        case A2dpStackEvent.CONNECTION_STATE_CONNECTED:
                        case A2dpStackEvent.CONNECTION_STATE_CONNECTING:
                            // Create a new state machine only when connecting to a device
                            if (mAdapterService.isVendorIntfEnabled())
                                mA2dpStackEvent =  stackEvent.valueInt;
                            if (mAdapterService.isTwsPlusDevice(device)) {
                                sm = getOrCreateStateMachine(device);
                                break;
                            }
                            if (!connectionAllowedCheckMaxDevices(device)) {
                                Log.e(TAG, "Cannot connect to " + device
                                        + " : too many connected devices");
                                mA2dpNativeInterface.disconnectA2dp(device);
                                return;
                            }
                            sm = getOrCreateStateMachine(device);
                            break;
                        default:
                            if (mAdapterService.isVendorIntfEnabled() &&
                                mA2dpStackEvent == A2dpStackEvent.CONNECTION_STATE_CONNECTED ||
                                mA2dpStackEvent == A2dpStackEvent.CONNECTION_STATE_CONNECTING) {
                                Log.d(TAG,"Reset local stack event value");
                                mA2dpStackEvent = EVENT_TYPE_NONE;
                            }
                            break;
                    }
                }
            } else {
                if (mAdapterService.isVendorIntfEnabled()) {
                    switch (sm.getConnectionState()) {
                      case BluetoothProfile.STATE_DISCONNECTED:
                        mA2dpStackEvent = stackEvent.valueInt;
                        break;
                      default:
                        mA2dpStackEvent = EVENT_TYPE_NONE;
                        break;
                    }
                }
            }
            if (sm == null) {
                Log.e(TAG, "Cannot process stack event: no state machine: " + stackEvent);
                return;
            }
            sm.sendMessage(A2dpStateMachine.STACK_EVENT, stackEvent);
        }
    }

    /**
     * The codec configuration for a device has been updated.
     *
     * @param device the remote device
     * @param codecStatus the new codec status
     * @param sameAudioFeedingParameters if true the audio feeding parameters
     * haven't been changed
     */
    @VisibleForTesting
    public void codecConfigUpdated(BluetoothDevice device, BluetoothCodecStatus codecStatus,
                            boolean sameAudioFeedingParameters) {
        Log.w(TAG, "codecConfigUpdated for device:" + device +
                                "sameAudioFeedingParameters: " + sameAudioFeedingParameters);

        // Log codec config and capability metrics
        BluetoothCodecConfig codecConfig = codecStatus.getCodecConfig();
        StatsLog.write(StatsLog.BLUETOOTH_A2DP_CODEC_CONFIG_CHANGED,
                mAdapterService.obfuscateAddress(device), codecConfig.getCodecType(),
                codecConfig.getCodecPriority(), codecConfig.getSampleRate(),
                codecConfig.getBitsPerSample(), codecConfig.getChannelMode(),
                codecConfig.getCodecSpecific1(), codecConfig.getCodecSpecific2(),
                codecConfig.getCodecSpecific3(), codecConfig.getCodecSpecific4());
        BluetoothCodecConfig[] codecCapabilities = codecStatus.getCodecsSelectableCapabilities();
        for (BluetoothCodecConfig codecCapability : codecCapabilities) {
            StatsLog.write(StatsLog.BLUETOOTH_A2DP_CODEC_CAPABILITY_CHANGED,
                    mAdapterService.obfuscateAddress(device), codecCapability.getCodecType(),
                    codecCapability.getCodecPriority(), codecCapability.getSampleRate(),
                    codecCapability.getBitsPerSample(), codecCapability.getChannelMode(),
                    codecConfig.getCodecSpecific1(), codecConfig.getCodecSpecific2(),
                    codecConfig.getCodecSpecific3(), codecConfig.getCodecSpecific4());
        }

        broadcastCodecConfig(device, codecStatus);

        // Inform the Audio Service about the codec configuration change,
        // so the Audio Service can reset accordingly the audio feeding
        // parameters in the Audio HAL to the Bluetooth stack.
        int rememberedVolume = -1;
        if (isActiveDevice(device) && !sameAudioFeedingParameters) {
            if (mAvrcp_ext != null)
                rememberedVolume = mAvrcp_ext.getVolume(device);
            mAudioManager.handleBluetoothA2dpActiveDeviceChange(device,
                    BluetoothProfile.STATE_CONNECTED, BluetoothProfile.A2DP,
                    true, rememberedVolume);
        }
    }
    void updateTwsChannelMode(int state, BluetoothDevice device) {
        if (mIsTwsPlusMonoSupported) {
            BluetoothDevice peerTwsDevice = mAdapterService.getTwsPlusPeerDevice(device);
            Log.d(TAG, "TwsChannelMode: " + mTwsPlusChannelMode);
            synchronized(mBtTwsLock) {
                if ("mono".equals(mTwsPlusChannelMode)) {
                    if ((state == BluetoothA2dp.STATE_PLAYING) && (peerTwsDevice!= null)
                         && peerTwsDevice.isConnected() && isA2dpPlaying(peerTwsDevice)) {
                        Log.d(TAG, "updateTwsChannelMode: send delay message to set dual-mono ");
                        Message msg = mHandler.obtainMessage(SET_EBDUALMONO_CFG);
                        mHandler.sendMessageDelayed(msg, DualMonoCfg_Timeout);
                    } else if (state == BluetoothA2dp.STATE_PLAYING) {
                        Log.d(TAG, "setparameters to Mono");
                        mAudioManager.setParameters("TwsChannelConfig=mono");
                    }
                    if ((state == BluetoothA2dp.STATE_NOT_PLAYING) &&
                           isA2dpPlaying(peerTwsDevice)) {
                        if (mHandler.hasMessages(SET_EBDUALMONO_CFG)) {
                            Log.d(TAG, "updateTwsChannelMode:remove delay message for dual-mono");
                            mHandler.removeMessages(SET_EBDUALMONO_CFG);
                        }
                    }
                } else if ("dual-mono".equals(mTwsPlusChannelMode)) {
                    if ((state == BluetoothA2dp.STATE_PLAYING) &&
                      (getConnectionState(peerTwsDevice) != BluetoothProfile.STATE_CONNECTED
                          || !isA2dpPlaying(peerTwsDevice))) {
                        Log.d(TAG, "updateTwsChannelMode: send delay message to set mono");
                        Message msg = mHandler.obtainMessage(SET_EBMONO_CFG);
                        mHandler.sendMessageDelayed(msg, MonoCfg_Timeout);
                    }
                    if ((state == BluetoothA2dp.STATE_PLAYING) && isA2dpPlaying(peerTwsDevice)) {
                        if (mHandler.hasMessages(SET_EBMONO_CFG)) {
                            Log.d(TAG, "updateTwsChannelMode: remove delay message to set mono");
                            mHandler.removeMessages(SET_EBMONO_CFG);
                        }
                    }
                    if ((state == BluetoothA2dp.STATE_NOT_PLAYING)
                          && isA2dpPlaying(peerTwsDevice)) {
                        Log.d(TAG, "setparameters to Mono");
                        mAudioManager.setParameters("TwsChannelConfig=mono");
                        mTwsPlusChannelMode = "mono";
                    }
                }
            }
        } else {
           Log.d(TAG,"TWS+ L/R to M feature not supported");
        }
    }

    public void broadcastReconfigureA2dp() {
        Log.w(TAG, "broadcastReconfigureA2dp(): set rcfg true to AudioManager");
        boolean isBAActive = false;
        BATService mBatService = BATService.getBATService();
        isBAActive = (mBatService != null) && (mBatService.isBATActive());
        Log.d(TAG," broadcastReconfigureA2dp: BA active " + isBAActive);
        // If BA is active, don't inform AudioManager about reconfig.
        if(isBAActive) {
            return;
        }
        mAudioManager.setParameters("reconfigA2dp=true");
    }


    private A2dpStateMachine getOrCreateStateMachine(BluetoothDevice device) {
        if (device == null) {
            Log.e(TAG, "getOrCreateStateMachine failed: device cannot be null");
            return null;
        }
        synchronized (mBtA2dpLock) {
            A2dpStateMachine sm = mStateMachines.get(device);
            if (sm != null) {
                return sm;
            }
            // Limit the maximum number of state machines to avoid DoS attack
            if (mStateMachines.size() >= MAX_A2DP_STATE_MACHINES) {
                Log.e(TAG, "Maximum number of A2DP state machines reached: "
                        + MAX_A2DP_STATE_MACHINES);
                return null;
            }
            if (DBG) {
                Log.d(TAG, "Creating a new state machine for " + device);
            }
            sm = A2dpStateMachine.make(device, this, mA2dpNativeInterface,
                                       mStateMachinesThread.getLooper());
            mStateMachines.put(device, sm);
            return sm;
        }
    }

    // This needs to run before any of the Audio Manager connection functions since
    // AVRCP needs to be aware that the audio device is changed before the Audio Manager
    // changes the volume of the output devices.
    private void updateAndBroadcastActiveDevice(BluetoothDevice device) {
        if (DBG) {
            Log.d(TAG, "updateAndBroadcastActiveDevice(" + device + ")");
        }

        synchronized (mStateMachines) {
            if (mFactory.getAvrcpTargetService() != null) {
                mFactory.getAvrcpTargetService().volumeDeviceSwitched(device);
            }

            mActiveDevice = device;
        }

        StatsLog.write(StatsLog.BLUETOOTH_ACTIVE_DEVICE_CHANGED, BluetoothProfile.A2DP,
                mAdapterService.obfuscateAddress(device));
        Intent intent = new Intent(BluetoothA2dp.ACTION_ACTIVE_DEVICE_CHANGED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT
                        | Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
        sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
    }

    private void broadcastCodecConfig(BluetoothDevice device, BluetoothCodecStatus codecStatus) {
        if (DBG) {
            Log.d(TAG, "broadcastCodecConfig(" + device + "): " + codecStatus);
        }
        Intent intent = new Intent(BluetoothA2dp.ACTION_CODEC_CONFIG_CHANGED);
        intent.putExtra(BluetoothCodecStatus.EXTRA_CODEC_STATUS, codecStatus);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT
                        | Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
        sendBroadcast(intent, A2dpService.BLUETOOTH_PERM);
    }

    private class BondStateChangedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction())) {
                return;
            }
            int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE,
                                           BluetoothDevice.ERROR);
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            Objects.requireNonNull(device, "ACTION_BOND_STATE_CHANGED with no EXTRA_DEVICE");
            bondStateChanged(device, state);
        }
    }

    /**
     * Process a change in the bonding state for a device.
     *
     * @param device the device whose bonding state has changed
     * @param bondState the new bond state for the device. Possible values are:
     * {@link BluetoothDevice#BOND_NONE},
     * {@link BluetoothDevice#BOND_BONDING},
     * {@link BluetoothDevice#BOND_BONDED}.
     */
    @VisibleForTesting
    void bondStateChanged(BluetoothDevice device, int bondState) {
        if (DBG) {
            Log.d(TAG, "Bond state changed for device: " + device + " state: " + bondState);
        }
        // Remove state machine if the bonding for a device is removed
        if (bondState != BluetoothDevice.BOND_NONE) {
            return;
        }
        synchronized (mBtA2dpLock) {
            A2dpStateMachine sm = mStateMachines.get(device);
            if (sm == null) {
                return;
            }
            if (sm.getConnectionState() != BluetoothProfile.STATE_DISCONNECTED) {
                return;
            }
            if (mFactory.getAvrcpTargetService() != null) {
                mFactory.getAvrcpTargetService().removeStoredVolumeForDevice(device);
            }

            removeStateMachine(device);
        }
    }

    private void removeStateMachine(BluetoothDevice device) {
        synchronized (mBtA2dpLock) {
            A2dpStateMachine sm = mStateMachines.get(device);
            if (sm == null) {
                Log.w(TAG, "removeStateMachine: device " + device
                        + " does not have a state machine");
                return;
            }
            Log.i(TAG, "removeStateMachine: removing state machine for device: " + device);
            sm.doQuit();
            sm.cleanup();
            mStateMachines.remove(device);
        }
    }


    /**
     * Update and initiate optional codec status change to native.
     *
     * @param device the device to change optional codec status
     */
    @VisibleForTesting
    public void updateOptionalCodecsSupport(BluetoothDevice device) {
        int previousSupport = getSupportsOptionalCodecs(device);
        boolean supportsOptional = false;
        boolean hasMandatoryCodec = false;

        synchronized (mBtA2dpLock) {
            A2dpStateMachine sm = mStateMachines.get(device);
            if (sm == null) {
                return;
            }
            BluetoothCodecStatus codecStatus = sm.getCodecStatus();
            if (codecStatus != null) {
                for (BluetoothCodecConfig config : codecStatus.getCodecsSelectableCapabilities()) {
                    if (config.isMandatoryCodec()) {
                        hasMandatoryCodec = true;
                    } else {
                        supportsOptional = true;
                    }
                }
            }
        }
        if (!hasMandatoryCodec) {
            // Mandatory codec(SBC) is not selectable. It could be caused by the remote device
            // select codec before native finish get codec capabilities. Stop use this codec
            // status as the reference to support/enable optional codecs.
            Log.i(TAG, "updateOptionalCodecsSupport: Mandatory codec is not selectable.");
            return;
        }

        if (previousSupport == BluetoothA2dp.OPTIONAL_CODECS_SUPPORT_UNKNOWN
                || supportsOptional != (previousSupport
                                    == BluetoothA2dp.OPTIONAL_CODECS_SUPPORTED)) {
            setSupportsOptionalCodecs(device, supportsOptional);
        }
        if (supportsOptional) {
            int enabled = getOptionalCodecsEnabled(device);
            switch (enabled) {
                case BluetoothA2dp.OPTIONAL_CODECS_PREF_UNKNOWN:
                    // Enable optional codec by default.
                    setOptionalCodecsEnabled(device, BluetoothA2dp.OPTIONAL_CODECS_PREF_ENABLED);
                    // Fall through intended
                case BluetoothA2dp.OPTIONAL_CODECS_PREF_ENABLED:
                    enableOptionalCodecs(device);
                    break;
                case BluetoothA2dp.OPTIONAL_CODECS_PREF_DISABLED:
                    disableOptionalCodecs(device);
                    break;
            }
        }
    }

    private void connectionStateChanged(BluetoothDevice device, int fromState, int toState) {
        if ((device == null) || (fromState == toState)) {
            return;
        }
        synchronized (mBtA2dpLock) {
            if (toState == BluetoothProfile.STATE_CONNECTED) {
                MetricsLogger.logProfileConnectionEvent(BluetoothMetricsProto.ProfileId.A2DP);
            }
            // Check if the device is disconnected - if unbond, remove the state machine
            if (toState == BluetoothProfile.STATE_DISCONNECTED) {
                int bondState = mAdapterService.getBondState(device);
                if (bondState == BluetoothDevice.BOND_NONE) {
                    if (mFactory.getAvrcpTargetService() != null) {
                        mFactory.getAvrcpTargetService().removeStoredVolumeForDevice(device);
                    }

                    removeStateMachine(device);
                }
            }
        }
    }

    /**
     * Receiver for processing device connection state changes.
     *
     * <ul>
     * <li> Update codec support per device when device is (re)connected
     * <li> Delete the state machine instance if the device is disconnected and unbond
     * </ul>
     */
    private class ConnectionStateChangedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED.equals(intent.getAction())) {
                return;
            }
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (device.getAddress().equals(BATService.mBAAddress)) {
                Log.d(TAG," ConnectionUpdate from BA, don't take action ");
                return;
            }
            int toState = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
            int fromState = intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, -1);
            connectionStateChanged(device, fromState, toState);
        }
    }

    /**
     * Binder object: must be a static class or memory leak may occur.
     */
    @VisibleForTesting
    static class BluetoothA2dpBinder extends IBluetoothA2dp.Stub
            implements IProfileServiceBinder {
        private A2dpService mService;

        private A2dpService getService() {
            if (!Utils.checkCaller()) {
                Log.w(TAG, "A2DP call not allowed for non-active user");
                return null;
            }

            if (mService != null && mService.isAvailable()) {
                return mService;
            }
            return null;
        }

        BluetoothA2dpBinder(A2dpService svc) {
            mService = svc;
        }

        @Override
        public void cleanup() {
            mService = null;
        }

        @Override
        public boolean connect(BluetoothDevice device) {
            A2dpService service = getService();
            if (service == null) {
                return false;
            }
            return service.connect(device);
        }

        @Override
        public boolean disconnect(BluetoothDevice device) {
            A2dpService service = getService();
            if (service == null) {
                return false;
            }
            return service.disconnect(device);
        }

        @Override
        public List<BluetoothDevice> getConnectedDevices() {
            A2dpService service = getService();
            if (service == null) {
                return new ArrayList<>(0);
            }
            return service.getConnectedDevices();
        }

        @Override
        public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
            A2dpService service = getService();
            if (service == null) {
                return new ArrayList<>(0);
            }
            return service.getDevicesMatchingConnectionStates(states);
        }

        @Override
        public int getConnectionState(BluetoothDevice device) {
            A2dpService service = getService();
            if (service == null) {
                return BluetoothProfile.STATE_DISCONNECTED;
            }
            return service.getConnectionState(device);
        }

        @Override
        public boolean setActiveDevice(BluetoothDevice device) {
            A2dpService service = getService();
            if (service == null) {
                return false;
            }
            return service.setActiveDevice(device);
        }

        @Override
        public BluetoothDevice getActiveDevice() {
            A2dpService service = getService();
            if (service == null) {
                return null;
            }
            return service.getActiveDevice();
        }

        @Override
        public boolean setPriority(BluetoothDevice device, int priority) {
            A2dpService service = getService();
            if (service == null) {
                return false;
            }
            return service.setPriority(device, priority);
        }

        @Override
        public int getPriority(BluetoothDevice device) {
            A2dpService service = getService();
            if (service == null) {
                return BluetoothProfile.PRIORITY_UNDEFINED;
            }
            return service.getPriority(device);
        }

        @Override
        public boolean isAvrcpAbsoluteVolumeSupported() {
            A2dpService service = getService();
            if (service == null) {
                return false;
            }
            return service.isAvrcpAbsoluteVolumeSupported();
        }

        @Override
        public void setAvrcpAbsoluteVolume(int volume) {
            A2dpService service = getService();
            if (service == null) {
                return;
            }
            service.setAvrcpAbsoluteVolume(volume);
        }

        @Override
        public boolean isA2dpPlaying(BluetoothDevice device) {
            A2dpService service = getService();
            if (service == null) {
                return false;
            }
            return service.isA2dpPlaying(device);
        }

        @Override
        public BluetoothCodecStatus getCodecStatus(BluetoothDevice device) {
            A2dpService service = getService();
            if (service == null) {
                return null;
            }
            return service.getCodecStatus(device);
        }

        @Override
        public void setCodecConfigPreference(BluetoothDevice device,
                                             BluetoothCodecConfig codecConfig) {
            A2dpService service = getService();
            if (service == null) {
                return;
            }
            service.setCodecConfigPreference(device, codecConfig);
        }

        @Override
        public void enableOptionalCodecs(BluetoothDevice device) {
            A2dpService service = getService();
            if (service == null) {
                return;
            }
            service.enableOptionalCodecs(device);
        }

        @Override
        public void disableOptionalCodecs(BluetoothDevice device) {
            A2dpService service = getService();
            if (service == null) {
                return;
            }
            service.disableOptionalCodecs(device);
        }

        public int supportsOptionalCodecs(BluetoothDevice device) {
            A2dpService service = getService();
            if (service == null) {
                return BluetoothA2dp.OPTIONAL_CODECS_SUPPORT_UNKNOWN;
            }
            AdapterService adService = AdapterService.getAdapterService();
            if(adService.isTwsPlusDevice(device)) {
                 Log.w(TAG, "Disable optional codec support for TWS+ device");
                 return BluetoothA2dp.OPTIONAL_CODECS_NOT_SUPPORTED;
            }
            return service.getSupportsOptionalCodecs(device);
        }

        public int getOptionalCodecsEnabled(BluetoothDevice device) {
            A2dpService service = getService();
            if (service == null) {
                return BluetoothA2dp.OPTIONAL_CODECS_PREF_UNKNOWN;
            }
            return service.getOptionalCodecsEnabled(device);
        }

        public void setOptionalCodecsEnabled(BluetoothDevice device, int value) {
            A2dpService service = getService();
            if (service == null) {
                return;
            }
            service.setOptionalCodecsEnabled(device, value);
        }
    }

    @Override
    public void dump(StringBuilder sb) {
        super.dump(sb);
        ProfileService.println(sb, "mActiveDevice: " + mActiveDevice);
        synchronized(mBtA2dpLock) {
            for (A2dpStateMachine sm : mStateMachines.values()) {
                sm.dump(sb);
            }
        }
        synchronized(mBtAvrcpLock) {
            if (mAvrcp_ext != null) {
                mAvrcp_ext.dump(sb);
                return;
            }
            if (mAvrcp != null) {
                mAvrcp.dump(sb);
            }
        }
    }
}
