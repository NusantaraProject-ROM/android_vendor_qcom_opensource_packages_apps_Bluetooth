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
import android.bluetooth.BluetoothAdapter;
import com.android.bluetooth.a2dpsink.A2dpSinkService;
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
import android.provider.Settings;
import android.support.annotation.GuardedBy;
import android.support.annotation.VisibleForTesting;
import android.util.Log;
import android.os.SystemProperties;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.android.bluetooth.Utils;
import com.android.bluetooth.avrcp.Avrcp;
import com.android.bluetooth.avrcp.Avrcp_ext;
import com.android.bluetooth.avrcp.AvrcpTargetService;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.ba.BATService;
import android.os.SystemClock;
import com.android.bluetooth.gatt.GattService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
    private static A2dpSinkService sA2dpSinkService;
    private static boolean mA2dpSrcSnkConcurrency;

    private BluetoothAdapter mAdapter;
    private AdapterService mAdapterService;
    private HandlerThread mStateMachinesThread;
    private Avrcp mAvrcp;
    private Avrcp_ext mAvrcp_ext;
    private final Object mBtA2dpLock = new Object();
    private final Object mBtAvrcpLock = new Object();
    private final Object mActiveDeviceLock = new Object();

    @VisibleForTesting
    A2dpNativeInterface mA2dpNativeInterface;
    private AudioManager mAudioManager;
    private A2dpCodecConfig mA2dpCodecConfig;

    @GuardedBy("mStateMachines")
    private BluetoothDevice mActiveDevice;
    private final ConcurrentMap<BluetoothDevice, A2dpStateMachine> mStateMachines =
            new ConcurrentHashMap<>();
    private static final int[] CONNECTING_CONNECTED_STATES = {
             BluetoothProfile.STATE_CONNECTING, BluetoothProfile.STATE_CONNECTED
             };
    // A2DP disconnet will be delayed at audioservice,
    // follwoing flags capture delay and delay time.
    private int mDisconnectDelay = 0;
    private long mDisconnectTime = 0;

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

    private static final long AptxBLEScanMask = 0x3000;
    private static final long Aptx_BLEScanEnable = 0x1000;
    private static final long Aptx_BLEScanDisable = 0x2000;
    private static final int SET_EBMONO_CFG = 1;
    private static final int MonoCfg_Timeout = 5000;
    private static boolean a2dpMulticast = false;

    private Handler mHandler = new Handler() {
        @Override
       public void handleMessage(Message msg)
       {
           switch (msg.what) {
               case SET_EBMONO_CFG:
                   Log.d(TAG, "setparameters to Mono");
                   mAudioManager.setParameters("TwsChannelConfig=mono");
                   mTwsPlusChannelMode = "mono";
                   break;
              default:
                   break;
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

        // Step 1: Get BluetoothAdapter, AdapterService, A2dpNativeInterface, AudioManager.
        // None of them can be null.
        mAdapter = Objects.requireNonNull(BluetoothAdapter.getDefaultAdapter(),
                "BluetoothAdapter cannot be null when A2dpService starts");
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
        if (mAdapterService.isVendorIntfEnabled()) {
            String twsPlusEnabled = SystemProperties.get("persist.vendor.btstack.enable.twsplus");
            if (!twsPlusEnabled.isEmpty() && "true".equals(twsPlusEnabled)) {
                mIsTwsPlusEnabled = true;
            }
            Log.i(TAG, "mMaxConnectedAudioDevices: " + mMaxConnectedAudioDevices);
            if (mIsTwsPlusEnabled) {
                mMaxConnectedAudioDevices = 2;
            } else if (mMaxConnectedAudioDevices > 2) {
                mMaxConnectedAudioDevices = 2;
                mSetMaxConnectedAudioDevices = mMaxConnectedAudioDevices;
            }
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
        Log.i(TAG, "Max connected audio devices set to " + mMaxConnectedAudioDevices);

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
        mA2dpNativeInterface.init(mMaxConnectedAudioDevices,
                                  mA2dpCodecConfig.codecConfigPriorities());

        // Step 7: Check if A2DP is in offload mode
        mA2dpOffloadEnabled = mAdapterService.isA2dpOffloadEnabled();
        if (DBG) {
            Log.d(TAG, "A2DP offload flag set to " + mA2dpOffloadEnabled);
        }
        mA2dpSrcSnkConcurrency= SystemProperties.getBoolean(
                                "persist.vendor.service.bt.a2dp_concurrency", false);
        if (DBG) {
            Log.d(TAG, "A2DP concurrency set to " + mA2dpSrcSnkConcurrency);
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

        // Step 11: get the a2dp multicast flag
        a2dpMulticast = SystemProperties.getBoolean("persist.vendor.service.bt.a2dp_multicast_enable", false);
        return true;
    }

    @Override
    protected boolean stop() {
        Log.i(TAG, "stop()");
        if (sA2dpService == null) {
            Log.w(TAG, "stop() called before start()");
            return true;
        }

        // Step 10: Store volume if there is an active device
        if (mActiveDevice != null && AvrcpTargetService.get() != null) {
            AvrcpTargetService.get().storeVolumeForDevice(mActiveDevice);
        }
        if (mActiveDevice != null && mAvrcp_ext != null)
            mAvrcp_ext.storeVolumeForDevice(mActiveDevice);

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
        // Step 1: Clear BluetoothAdapter, AdapterService, A2dpNativeInterface, AudioManager
        mAudioManager = null;
        mA2dpNativeInterface = null;
        mAdapterService = null;
        mAdapter = null;

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
            disconnectExisting = false;
            if (!connectionAllowedCheckMaxDevices(device) && !disconnectExisting) {
                Log.e(TAG, "Cannot connect to " + device + " : too many connected devices");
                return false;
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
            if (mA2dpSrcSnkConcurrency) {
                sA2dpSinkService = A2dpSinkService.getA2dpSinkService();
                List<BluetoothDevice> srcDevs = sA2dpSinkService.getConnectedDevices();
                for ( BluetoothDevice src : srcDevs ) {
                    Log.d(TAG, "calling sink disconnect to " + src);
                    sA2dpSinkService.disconnect(src);
                }
            }

            smConnect.sendMessage(A2dpStateMachine.CONNECT);
            return true;
        }
    }

    public boolean disconnect(BluetoothDevice device) {
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
    private boolean isConnectionAllowed(BluetoothDevice device, boolean tws_connected,
                                        int num_connected) {
        if (!mIsTwsPlusEnabled && mAdapterService.isTwsPlusDevice(device)) {
           Log.d(TAG, "No TWSPLUS connections as It is not Enabled");
           return false;
        }
        if (num_connected == 0) return true;

        List <BluetoothDevice> connectingConnectedDevices =
                  getDevicesMatchingConnectionStates(CONNECTING_CONNECTED_STATES);
        BluetoothDevice mConnDev = null;
        if(!connectingConnectedDevices.isEmpty())
            mConnDev = connectingConnectedDevices.get(0);
        if (mA2dpStackEvent == A2dpStackEvent.CONNECTION_STATE_CONNECTING ||
            mA2dpStackEvent == A2dpStackEvent.CONNECTION_STATE_CONNECTED) {
            if ((!mAdapterService.isTwsPlusDevice(device) && tws_connected) ||
                (mAdapterService.isTwsPlusDevice(device) && !tws_connected)) {
                Log.d(TAG,"isConnectionAllowed: incoming connection not allowed");
                mA2dpStackEvent = EVENT_TYPE_NONE;
                return false;
            }
        }
        if (num_connected > 1 &&
           ((!tws_connected && mAdapterService.isTwsPlusDevice(device)) ||
           (tws_connected && !mAdapterService.isTwsPlusDevice(device)))) {
            Log.d(TAG,"isConnectionAllowed: Max connections reached");
            return false;
        }
        if (!tws_connected && mAdapterService.isTwsPlusDevice(device)) {
            Log.d(TAG,"isConnectionAllowed: Disconnect legacy device for outgoing TWSP connection");
            disconnectExisting = true;
            return false;
        }
        if (tws_connected && mAdapterService.isTwsPlusDevice(device)) {
            //if (num_connected == mMaxConnectedAudioDevices) {
            if (num_connected > 1) {
                Log.d(TAG,"isConnectionAllowed: Max TWS connected, disconnect first");
                return false;
            } else if(mConnDev != null && mAdapterService.getTwsPlusPeerAddress(mConnDev).equals(device.getAddress())) {
                Log.d(TAG,"isConnectionAllowed: Peer earbud pair allow connection");
                return true;
            } else {
                Log.d(TAG,"isConnectionAllowed: Unpaired earbud, disconnect previous TWS+ device");
                disconnectExisting = true;
                return false;
            }
        } else if (tws_connected && !mAdapterService.isTwsPlusDevice(device)) {
            Log.d(TAG,"isConnectionAllowed: Disconnect tws device to connect to legacy headset");
            disconnectExisting = true;
            return false;
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
        boolean tws_device = false;
        // Count devices that are in the process of connecting or already connected
        synchronized (mBtA2dpLock) {
            for (A2dpStateMachine sm : mStateMachines.values()) {
                switch (sm.getConnectionState()) {
                    case BluetoothProfile.STATE_CONNECTING:
                    case BluetoothProfile.STATE_CONNECTED:
                        if (Objects.equals(device, sm.getDevice())) {
                            return true;    // Already connected or accounted for
                        }
                        if (tws_device == false) {
                            tws_device = mAdapterService.isTwsPlusDevice(sm.getDevice());
                        }
                        connected++;
                        break;
                    default:
                        break;
                }
            }
        }
        Log.d(TAG,"connectionAllowedCheckMaxDevices connected = " + connected);
        if (mAdapterService.isVendorIntfEnabled() &&
            (tws_device || mAdapterService.isTwsPlusDevice(device) ||
            (tws_device && connected == mMaxConnectedAudioDevices &&
            !mAdapterService.isTwsPlusDevice(device)))) {
            return isConnectionAllowed(device, tws_device, connected);
        }
        if (mSetMaxConnectedAudioDevices == 1 &&
            connected == mSetMaxConnectedAudioDevices) {
            disconnectExisting = true;
            return true;
        }
        return (connected < mMaxConnectedAudioDevices);
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
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
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
        int priority = getPriority(device);
        int bondState = mAdapterService.getBondState(device);
        // Allow this connection only if the device is bonded. Any attempt to connect while
        // bonding would potentially lead to an unauthorized connection.
        if (bondState != BluetoothDevice.BOND_BONDED) {
            Log.w(TAG, "okToConnect: return false, bondState=" + bondState);
            return false;
        } else if (priority != BluetoothProfile.PRIORITY_UNDEFINED
                && priority != BluetoothProfile.PRIORITY_ON
                && priority != BluetoothProfile.PRIORITY_AUTO_CONNECT) {
            // Otherwise, reject the connection if priority is not valid.
            Log.w(TAG, "okToConnect: return false, priority=" + priority);
            return false;
        }
        return true;
    }

    List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        List<BluetoothDevice> devices = new ArrayList<>();
        Set<BluetoothDevice> bondedDevices = mAdapter.getBondedDevices();
        synchronized (mBtA2dpLock) {
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
                for (int i = 0; i < states.length; i++) {
                    if (connectionState == states[i]) {
                        devices.add(device);
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

    private void removeActiveDevice(boolean forceStopPlayingAudio) {
        BluetoothDevice previousActiveDevice = mActiveDevice;
        synchronized (mBtA2dpLock) {
            // Clear the active device
            mActiveDevice = null;
            // This needs to happen before we inform the audio manager that the device
            // disconnected. Please see comment in broadcastActiveDevice() for why.
            broadcastActiveDevice(null);

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
            if(isBAActive) {
                suppressNoisyIntent = true;
                Log.d(TAG," BA Active, suppress noisy intent");
            }
            if (mAdapterService.isTwsPlusDevice(previousActiveDevice) &&
                mDummyDevice != null) {
                previousActiveDevice = mDummyDevice;
                mDummyDevice = null;
            }
            mDisconnectDelay =
                    mAudioManager.setBluetoothA2dpDeviceConnectionStateSuppressNoisyIntent(
                    previousActiveDevice, BluetoothProfile.STATE_DISCONNECTED,
                    BluetoothProfile.A2DP, suppressNoisyIntent, -1);
            if (mDisconnectDelay > 0) {
                mDisconnectTime = SystemClock.uptimeMillis();
            }
            // Make sure the Active device in native layer is set to null and audio is off
            if (!mA2dpNativeInterface.setActiveDevice(null)) {
                Log.w(TAG, "setActiveDevice(null): Cannot remove active device in native "
                        + "layer");
            }
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
        synchronized (mActiveDeviceLock) {
            return setActiveDeviceInternal(device);
        }
    }
    private boolean setActiveDeviceInternal(BluetoothDevice device) {
        boolean deviceChanged;
        BluetoothCodecStatus codecStatus = null;
        BluetoothDevice previousActiveDevice = mActiveDevice;
        boolean isBAActive = false;
        Log.w(TAG, "setActiveDevice(" + device + "): previous is " + previousActiveDevice);

        if (previousActiveDevice != null && AvrcpTargetService.get() != null) {
            AvrcpTargetService.get().storeVolumeForDevice(previousActiveDevice);
        } else if (previousActiveDevice != null && mAvrcp_ext != null) {
            //Store volume only if SHO is triggered or output device other than BT is selected
            mAvrcp_ext.storeVolumeForDevice(previousActiveDevice);
        }
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
            if (!mA2dpNativeInterface.setActiveDevice(device)) {
                Log.e(TAG, "setActiveDevice(" + device + "): Cannot set as active in native layer");
                return false;
            }
            codecStatus = sm.getCodecStatus();
            mActiveDevice = device;
            // This needs to happen before we inform the audio manager that the device
            // disconnected. Please see comment in broadcastActiveDevice() for why.
            broadcastActiveDevice(mActiveDevice);
            Log.w(TAG, "setActiveDevice coming out of mutex lock");
        }
        if (deviceChanged &&
            (mDummyDevice == null || !mAdapterService.isTwsPlusDevice(mActiveDevice))) {
            if(mAvrcp_ext != null)
                mAvrcp_ext.setActiveDevice(device);
            if (mAdapterService.isTwsPlusDevice(device) && mDummyDevice == null) {
                Log.d(TAG,"set dummy device for tws+");
                mDummyDevice = mAdapter.getRemoteDevice("FA:CE:FA:CE:FA:CE");
            }
            // Send an intent with the active device codec config
            if (codecStatus != null) {
                broadcastCodecConfig(mActiveDevice, codecStatus);
            }
            int rememberedVolume = -1;
            if (AvrcpTargetService.get() != null) {
                AvrcpTargetService.get().volumeDeviceSwitched(device);

                rememberedVolume = AvrcpTargetService.get()
                        .getRememberedVolumeForDevice(device);
            } else if (mAdapterService.isVendorIntfEnabled()) {
                rememberedVolume = mAvrcp_ext.getVolume(device);
                Log.d(TAG,"volume = " + rememberedVolume);
            }
            // Make sure the Audio Manager knows the previous Active device is disconnected,
            // and the new Active device is connected.
            // Also, mute and unmute the output during the switch to avoid audio glitches.
            boolean wasMuted = false;
            if (previousActiveDevice != null) {
                if (!mAudioManager.isStreamMute(AudioManager.STREAM_MUSIC)) {
                   mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                                                 AudioManager.ADJUST_MUTE,
                                                 mAudioManager.FLAG_BLUETOOTH_ABS_VOLUME);
                   wasMuted = true;
                }
                if (!a2dpMulticast) {
                    if (mDummyDevice != null &&
                        mAdapterService.isTwsPlusDevice(previousActiveDevice)) {
                        mAudioManager.setBluetoothA2dpDeviceConnectionStateSuppressNoisyIntent(
                                mDummyDevice, BluetoothProfile.STATE_DISCONNECTED,
                                BluetoothProfile.A2DP, true, -1);
                        mDummyDevice = null;
                    } else  {
                        mAudioManager.setBluetoothA2dpDeviceConnectionStateSuppressNoisyIntent(
                                previousActiveDevice, BluetoothProfile.STATE_DISCONNECTED,
                                BluetoothProfile.A2DP, true, -1);
                    }
                }
            }
            // Check if ther is any delay set on audioservice for previous
            // disconnect, if so then need to serialise disconnect/connect
            // requests to audioservice, wait till prev disconnect is completed
            if (mDisconnectDelay > 0) {
                long currentTime = SystemClock.uptimeMillis();
                if (mDisconnectDelay > (currentTime - mDisconnectTime)) {
                    try {
                        Log.d(TAG, "Enter wait for previous disconnect");
                        Thread.sleep(mDisconnectDelay - (currentTime - mDisconnectTime));
                        Log.d(TAG, "Exiting Wait for previous disconnect");
                    } catch (InterruptedException e) {
                        Log.e(TAG, "setactive was interrupted");
                    }
                }
                mDisconnectDelay = 0;
                mDisconnectTime = 0;
            }

            if (!isBAActive) {
                if (mDummyDevice == null) {
                    mAudioManager.setBluetoothA2dpDeviceConnectionStateSuppressNoisyIntent(
                            mActiveDevice, BluetoothProfile.STATE_CONNECTED, BluetoothProfile.A2DP,
                            true, rememberedVolume);
                } else {
                    mAudioManager.setBluetoothA2dpDeviceConnectionStateSuppressNoisyIntent(
                            mDummyDevice, BluetoothProfile.STATE_CONNECTED, BluetoothProfile.A2DP,
                            true, rememberedVolume);
                }
            }

            // Inform the Audio Service about the codec configuration
            // change, so the Audio Service can reset accordingly the audio
            // feeding parameters in the Audio HAL to the Bluetooth stack.
            String offloadSupported =
                 SystemProperties.get("persist.vendor.btstack.enable.splita2dp");
            if (!(offloadSupported.isEmpty() || "true".equals(offloadSupported))) {
                mAudioManager.handleBluetoothA2dpDeviceConfigChange(device);
            }
            if (wasMuted) {
               mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                                          AudioManager.ADJUST_UNMUTE,
                                          mAudioManager.FLAG_BLUETOOTH_ABS_VOLUME);
            }
            // Don't update the absVolume flags when disconnect one device in multicast mode
            if (!a2dpMulticast || previousActiveDevice == null) {
                if(mAvrcp_ext != null)
                    mAvrcp_ext.setAbsVolumeFlag(device);
            }
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
        Settings.Global.putInt(getContentResolver(),
                Settings.Global.getBluetoothA2dpSinkPriorityKey(device.getAddress()), priority);
        if (DBG) {
            Log.d(TAG, "Saved priority " + device + " = " + priority);
        }
        return true;
    }

    public int getPriority(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH_ADMIN permission");
        int priority = Settings.Global.getInt(getContentResolver(),
                Settings.Global.getBluetoothA2dpSinkPriorityKey(device.getAddress()),
                BluetoothProfile.PRIORITY_UNDEFINED);
        return priority;
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
        if (AvrcpTargetService.get() != null) {
            AvrcpTargetService.get().sendVolumeChanged(volume);
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

    public void storeDeviceAudioVolume(BluetoothDevice device) {
        if (device != null)
        {
            if (AvrcpTargetService.get() != null) {
                AvrcpTargetService.get().storeVolumeForDevice(device);
            } else if (mAvrcp_ext != null) {
                //store volume in multi-a2dp for the device doesn't set as active
                mAvrcp_ext.storeVolumeForDevice(device);
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
        synchronized (mBtA2dpLock) {
            if (device == null) {
                device = mActiveDevice;
            }
            if (device == null) {
                return null;
            }
            A2dpStateMachine sm = mStateMachines.get(device);
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

        if((codecConfig.getCodecSpecific4() & AptxBLEScanMask) > 0) {
            GattService mGattService = GattService.getGattService();

            if(mGattService != null) {
                long mScanMode = codecConfig.getCodecSpecific4() & AptxBLEScanMask;
                if(mScanMode == Aptx_BLEScanEnable) {
                    mGattService.setAptXLowLatencyMode(false);
                }
                else if(mScanMode == Aptx_BLEScanDisable) {
                    Log.w(TAG, "Disable BLE scanning to support aptX LL Mode");
                    mGattService.setAptXLowLatencyMode(true);
                }
            }
        }
        mA2dpCodecConfig.setCodecConfigPreference(device, codecConfig);
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
        mA2dpCodecConfig.enableOptionalCodecs(device);
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
        mA2dpCodecConfig.disableOptionalCodecs(device);
    }

    public int getSupportsOptionalCodecs(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH_ADMIN permission");
        int support = Settings.Global.getInt(getContentResolver(),
                Settings.Global.getBluetoothA2dpSupportsOptionalCodecsKey(device.getAddress()),
                BluetoothA2dp.OPTIONAL_CODECS_SUPPORT_UNKNOWN);
        return support;
    }

    public void setSupportsOptionalCodecs(BluetoothDevice device, boolean doesSupport) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH_ADMIN permission");
        int value = doesSupport ? BluetoothA2dp.OPTIONAL_CODECS_SUPPORTED
                : BluetoothA2dp.OPTIONAL_CODECS_NOT_SUPPORTED;
        Settings.Global.putInt(getContentResolver(),
                Settings.Global.getBluetoothA2dpSupportsOptionalCodecsKey(device.getAddress()),
                value);
    }

    public int getOptionalCodecsEnabled(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH_ADMIN permission");
        return Settings.Global.getInt(getContentResolver(),
                Settings.Global.getBluetoothA2dpOptionalCodecsEnabledKey(device.getAddress()),
                BluetoothA2dp.OPTIONAL_CODECS_PREF_UNKNOWN);
    }

    public void setOptionalCodecsEnabled(BluetoothDevice device, int value) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH_ADMIN permission");
        if (value != BluetoothA2dp.OPTIONAL_CODECS_PREF_UNKNOWN
                && value != BluetoothA2dp.OPTIONAL_CODECS_PREF_DISABLED
                && value != BluetoothA2dp.OPTIONAL_CODECS_PREF_ENABLED) {
            Log.w(TAG, "Unexpected value passed to setOptionalCodecsEnabled:" + value);
            return;
        }
        Settings.Global.putInt(getContentResolver(),
                Settings.Global.getBluetoothA2dpOptionalCodecsEnabledKey(device.getAddress()),
                value);
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
            if (mA2dpSrcSnkConcurrency &&
                ( A2dpStackEvent.CONNECTION_STATE_CONNECTING == stackEvent.valueInt ||
                  A2dpStackEvent.CONNECTION_STATE_CONNECTED == stackEvent.valueInt )) {
                sA2dpSinkService = A2dpSinkService.getA2dpSinkService();
                List<BluetoothDevice> srcDevs = sA2dpSinkService.getConnectedDevices();
                for ( BluetoothDevice src : srcDevs ) {
                    Log.d(TAG, "calling sink disconnect to " + src);
                    sA2dpSinkService.disconnect(src);
                }
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
    void codecConfigUpdated(BluetoothDevice device, BluetoothCodecStatus codecStatus,
                            boolean sameAudioFeedingParameters) {
        Log.w(TAG, "codecConfigUpdated for device:" + device +
                                "sameAudioFeedingParameters: " + sameAudioFeedingParameters);
        broadcastCodecConfig(device, codecStatus);

        // Inform the Audio Service about the codec configuration change,
        // so the Audio Service can reset accordingly the audio feeding
        // parameters in the Audio HAL to the Bluetooth stack.
        if (isActiveDevice(device) && !sameAudioFeedingParameters) {
            mAudioManager.handleBluetoothA2dpDeviceConfigChange(device);
        }
    }
    void updateTwsChannelMode(int state, BluetoothDevice device) {
       if (mIsTwsPlusMonoSupported) {
         BluetoothDevice peerTwsDevice = mAdapterService.getTwsPlusPeerDevice(device);
         Log.d(TAG, "TwsChannelMode: " + mTwsPlusChannelMode);
         if ((state == BluetoothA2dp.STATE_PLAYING) && ("mono".equals(mTwsPlusChannelMode))) {
             if ((peerTwsDevice!= null) && peerTwsDevice.isConnected() && isA2dpPlaying(peerTwsDevice)) {
                 Log.d(TAG, "setparameters to Dual-Mono");
                 mAudioManager.setParameters("TwsChannelConfig=dual-mono");
                mTwsPlusChannelMode = "dual-mono";
             }
         } else if ("dual-mono".equals(mTwsPlusChannelMode)) {
            if ((state == BluetoothA2dp.STATE_PLAYING) && (getConnectionState(peerTwsDevice) != BluetoothProfile.STATE_CONNECTED)) {
               Log.d(TAG, "updateTwsChannelMode: send delay message ");
               Message msg = mHandler.obtainMessage(SET_EBMONO_CFG);
               mHandler.sendMessageDelayed(msg, MonoCfg_Timeout);
            }
            if ((state == BluetoothA2dp.STATE_PLAYING) && isA2dpPlaying(peerTwsDevice)) {
               if (mHandler.hasMessages(SET_EBMONO_CFG)) {
                 Log.d(TAG, "updateTwsChannelMode: remove delay message ");
                 mHandler.removeMessages(SET_EBMONO_CFG);
               }
            }
            if ((state == BluetoothA2dp.STATE_NOT_PLAYING) && isA2dpPlaying(peerTwsDevice)) {
               Log.d(TAG, "setparameters to Mono");
               mAudioManager.setParameters("TwsChannelConfig=mono");
               mTwsPlusChannelMode = "mono";
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

    private void broadcastActiveDevice(BluetoothDevice device) {
        if (DBG) {
            Log.d(TAG, "broadcastActiveDevice(" + device + ")");
        }

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

    public void updateOptionalCodecsSupport(BluetoothDevice device) {
        int previousSupport = getSupportsOptionalCodecs(device);
        boolean supportsOptional = false;

        synchronized (mBtA2dpLock) {
            A2dpStateMachine sm = mStateMachines.get(device);
            if (sm == null) {
                return;
            }
            BluetoothCodecStatus codecStatus = sm.getCodecStatus();
            if (codecStatus != null) {
                for (BluetoothCodecConfig config : codecStatus.getCodecsSelectableCapabilities()) {
                    if (!config.isMandatoryCodec()) {
                        supportsOptional = true;
                        break;
                    }
                }
            }
        }
        if (previousSupport == BluetoothA2dp.OPTIONAL_CODECS_SUPPORT_UNKNOWN
                || supportsOptional != (previousSupport
                                    == BluetoothA2dp.OPTIONAL_CODECS_SUPPORTED)) {
            setSupportsOptionalCodecs(device, supportsOptional);
        }
        if (supportsOptional) {
            int enabled = getOptionalCodecsEnabled(device);
            if (enabled == BluetoothA2dp.OPTIONAL_CODECS_PREF_ENABLED) {
                enableOptionalCodecs(device);
            } else if (enabled == BluetoothA2dp.OPTIONAL_CODECS_PREF_DISABLED) {
                disableOptionalCodecs(device);
            }
        }
    }

    private void connectionStateChanged(BluetoothDevice device, int fromState, int toState) {
        if ((device == null) || (fromState == toState)) {
            return;
        }
        synchronized (mBtA2dpLock) {
            if (toState == BluetoothProfile.STATE_CONNECTED) {
                // Each time a device connects, we want to re-check if it supports optional
                // codecs (perhaps it's had a firmware update, etc.) and save that state if
                // it differs from what we had saved before.
                updateOptionalCodecsSupport(device);
            }
            // Check if the device is disconnected - if unbond, remove the state machine
            if (toState == BluetoothProfile.STATE_DISCONNECTED) {
                int bondState = mAdapterService.getBondState(device);
                if (bondState == BluetoothDevice.BOND_NONE) {
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
