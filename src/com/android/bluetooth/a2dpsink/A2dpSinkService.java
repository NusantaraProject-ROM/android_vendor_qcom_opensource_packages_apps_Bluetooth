/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.bluetooth.a2dpsink;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAudioConfig;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothUuid;
import android.bluetooth.IBluetoothA2dpSink;
<<<<<<< HEAD
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.ParcelUuid;
import android.os.SystemProperties;
import android.provider.Settings;
=======
>>>>>>> e94de994c16aa83274259da3c313a88d57b26b48
import android.util.Log;
import android.widget.Toast;

import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.ProfileService;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
<<<<<<< HEAD
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
=======
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
>>>>>>> e94de994c16aa83274259da3c313a88d57b26b48

/**
 * Provides Bluetooth A2DP Sink profile, as a service in the Bluetooth application.
 * @hide
 */
public class A2dpSinkService extends ProfileService {
    private static final String TAG = "A2dpSinkService";
    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);
    static final int MAXIMUM_CONNECTED_DEVICES = 1;

<<<<<<< HEAD
    /* HashMap of A2dpSinkStateMachines for remote connected devices*/
    private final ConcurrentMap<BluetoothDevice, A2dpSinkStateMachine> mStateMachines =
            new ConcurrentHashMap<>();
    private HandlerThread mStateMachinesThread;
    private final Object mBtA2dpLock = new Object();
    private AdapterService mAdapterService;

    private A2dpSinkStateMachine mStateMachine;
    private static A2dpSinkService sA2dpSinkService;
    protected static BluetoothDevice mStreamingDevice;

    private static int mMaxA2dpSinkConnections = 1;
    public static final int MAX_ALLOWED_SINK_CONNECTIONS = 2;

    static {
        classInitNative();
    }
=======
    private final BluetoothAdapter mAdapter;
    protected Map<BluetoothDevice, A2dpSinkStateMachine> mDeviceStateMap =
            new ConcurrentHashMap<>(1);
>>>>>>> e94de994c16aa83274259da3c313a88d57b26b48

    private A2dpSinkStreamHandler mA2dpSinkStreamHandler;
    private static A2dpSinkService sService;

    static {
        classInitNative();
    }

    @Override
    protected boolean start() {
<<<<<<< HEAD
        if (DBG) {
            Log.d(TAG, "start()");
        }

        initNative();
        mStateMachines.clear();
        mStateMachinesThread = new HandlerThread("A2dpSinkService.StateMachines");
        mStateMachinesThread.start();

        mAdapterService = Objects.requireNonNull(AdapterService.getAdapterService(),
                "AdapterService cannot be null when A2dpService starts");

        mMaxA2dpSinkConnections = Math.min(
                SystemProperties.getInt("persist.vendor.bt.a2dp.sink_conn", 1),
                MAX_ALLOWED_SINK_CONNECTIONS);
        // Start the media browser service.
        Intent startIntent = new Intent(this, BluetoothMediaBrowserService.class);
        startService(startIntent);
        setA2dpSinkService(this);
=======
        initNative();
        sService = this;
        mA2dpSinkStreamHandler = new A2dpSinkStreamHandler(this, this);
>>>>>>> e94de994c16aa83274259da3c313a88d57b26b48
        return true;
    }

    @Override
    protected boolean stop() {
        for (A2dpSinkStateMachine stateMachine : mDeviceStateMap.values()) {
            stateMachine.quitNow();
        }
<<<<<<< HEAD
        setA2dpSinkService(null);

        // Step 4: Destroy state machines and stop handler thread
        synchronized (mBtA2dpLock) {
            for (A2dpSinkStateMachine sm : mStateMachines.values()) {
                sm.doQuit();
                sm.cleanup();
            }
            mStateMachines.clear();
        }
        mStateMachinesThread.quitSafely();
        mStateMachinesThread = null;

        Intent stopIntent = new Intent(this, BluetoothMediaBrowserService.class);
        stopService(stopIntent);
        return true;
    }

    @Override
    protected void cleanup() {
        cleanupNative();
    }

    protected void removeStateMachine(BluetoothDevice device) {
        synchronized (mBtA2dpLock) {
            A2dpSinkStateMachine sm = mStateMachines.get(device);
            if (sm == null) {
                Log.e(TAG, "State Machine not found for device:" + device);
                return;
            }
            mStateMachines.remove(device);
            sm.doQuit();
            sm.cleanup();
            sm = null;
         }
=======
        sService = null;
        return true;
    }

    public static A2dpSinkService getA2dpSinkService() {
        return sService;
>>>>>>> e94de994c16aa83274259da3c313a88d57b26b48
    }

    public A2dpSinkService() {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    protected A2dpSinkStateMachine newStateMachine(BluetoothDevice device) {
        return new A2dpSinkStateMachine(device, this);
    }

<<<<<<< HEAD
    public boolean connect(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH ADMIN permission");

        if (DBG) Log.d(TAG, "connect(): " + device);
        if (getPriority(device) == BluetoothProfile.PRIORITY_OFF) {
            return false;
        }
        if (!BluetoothUuid.isUuidPresent(mAdapterService.getRemoteUuids(device),
                                         BluetoothUuid.AudioSource)) {
            Log.e(TAG, "Cannot connect to " + device + " : Remote does not have A2DP Source UUID");
            return false;
        }

        A2dpSinkStateMachine sm = null;
        synchronized (mBtA2dpLock) {
            sm = getOrCreateStateMachine(device);
            if (sm != null) {
                int connectionState = sm.getConnectionState();
                if (connectionState == BluetoothProfile.STATE_CONNECTED
                        || connectionState == BluetoothProfile.STATE_CONNECTING) {
                    Log.e(TAG, "Device (" + device + ") is already connected/connecting. Ignore");
                    return false;
                }
            } else if (sm == null) {
                return false;
            }
            sm.sendMessage(A2dpSinkStateMachine.CONNECT);
        }
        return true;
    }

    boolean disconnect(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH ADMIN permission");

        if (DBG) {
            Log.d(TAG, "disconnect(): " + device);
        }

        synchronized (mBtA2dpLock) {
            A2dpSinkStateMachine sm = mStateMachines.get(device);
            if (sm == null) {
                Log.e(TAG, "Ignored disconnect request for " + device + " : no state machine");
                return false;
            }
            // State check before Disconnect
            int connectionState = sm.getConnectionState();
            if (connectionState != BluetoothProfile.STATE_CONNECTED
                    && connectionState != BluetoothProfile.STATE_CONNECTING) {
                return false;
            }
            sm.sendMessage(A2dpSinkStateMachine.DISCONNECT);
            return true;
        }
    }

    List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        List<BluetoothDevice> deviceList = new ArrayList<BluetoothDevice>();
        synchronized (mStateMachines) {
            Set<BluetoothDevice> bondedDevices = mAdapter.getBondedDevices();
            int connectionState;

            for (BluetoothDevice device : bondedDevices) {
                ParcelUuid[] featureUuids = device.getUuids();
                if (!BluetoothUuid.isUuidPresent(featureUuids, BluetoothUuid.AudioSource)) {
                    continue;
                }
                connectionState = BluetoothProfile.STATE_DISCONNECTED;
                A2dpSinkStateMachine sm = mStateMachines.get(device);
                if (sm != null) {
                    connectionState = sm.getConnectionState();
                }
                for (int i = 0; i < states.length; i++) {
                    if (connectionState == states[i]) {
                        deviceList.add(device);
                    }
                }
            }
        }
        return deviceList;
    }

    public int getConnectionState(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        synchronized (mBtA2dpLock) {
            A2dpSinkStateMachine sm = mStateMachines.get(device);
            if (sm != null) {
                return sm.getConnectionState();
            }
        }
        return BluetoothProfile.STATE_DISCONNECTED;
    }

    public boolean setPriority(BluetoothDevice device, int priority) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH_ADMIN permission");
        if (DBG) {
            Log.d(TAG, "Saved priority " + device + " = " + priority);
        }
        AdapterService.getAdapterService().getDatabase()
                .setProfilePriority(device, BluetoothProfile.A2DP_SINK, priority);
        return true;
    }

    public int getPriority(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH_ADMIN permission");
        return AdapterService.getAdapterService().getDatabase()
                .getProfilePriority(device, BluetoothProfile.A2DP_SINK);
    }

    /**
     * Called by AVRCP controller to provide information about the last user intent on CT.
     *
     * If the user has pressed play in the last attempt then A2DP Sink component will grant focus to
     * any incoming sound from the phone (and also retain focus for a few seconds before
     * relinquishing. On the other hand if the user has pressed pause/stop then the A2DP sink
     * component will take the focus away but also notify the stack to throw away incoming data.
     */
    public void informAvrcpPassThroughCmd(BluetoothDevice device, int keyCode, int keyState) {
        A2dpSinkStateMachine mStateMachine = null;
        synchronized (mBtA2dpLock) {
            mStateMachine = mStateMachines.get(device);
            if (mStateMachine == null) {
                Log.w(TAG, "state machine is not present for device:" + device);
                return;
            }
        }
        if (mStateMachine != null) {
            if (keyCode == AvrcpControllerService.PASS_THRU_CMD_ID_PLAY
                    && keyState == AvrcpControllerService.KEY_STATE_RELEASED) {
                mStateMachine.sendMessage(A2dpSinkStateMachine.EVENT_AVRCP_CT_PLAY);
            } else if ((keyCode == AvrcpControllerService.PASS_THRU_CMD_ID_PAUSE
                    || keyCode == AvrcpControllerService.PASS_THRU_CMD_ID_STOP)
                    && keyState == AvrcpControllerService.KEY_STATE_RELEASED) {
                mStateMachine.sendMessage(A2dpSinkStateMachine.EVENT_AVRCP_CT_PAUSE);
            }
        }
    }

    /**
     * Called by AVRCP controller to provide information about the last user intent on TG.
     *
     * Tf the user has pressed pause on the TG then we can preempt streaming music. This is opposed
     * to when the streaming stops abruptly (jitter) in which case we will wait for sometime before
     * stopping playback.
     */
    public void informTGStatePlaying(BluetoothDevice device, boolean isPlaying) {
        Log.d(TAG, "informTGStatePlaying: device: " + device
                + ", mStreamingDevice:" + mStreamingDevice);
        A2dpSinkStateMachine mStateMachine = null;
        synchronized (mBtA2dpLock) {
            mStateMachine = mStateMachines.get(device);
            if (mStateMachine == null) {
                return;
            }
        }
        if (mStateMachine != null) {
            if (!isPlaying) {
                mStateMachine.sendMessage(A2dpSinkStateMachine.EVENT_AVRCP_TG_PAUSE);
            } else {
                // Soft-Handoff from AVRCP Cmd (if received before AVDTP_START)
                initiateHandoffOperations(device);
                if (mStreamingDevice != null && !mStreamingDevice.equals(device)) {
                    Log.d(TAG, "updating streaming device after avrcp status command");
                    mStreamingDevice = device;
                }
                mStateMachine.sendMessage(A2dpSinkStateMachine.EVENT_AVRCP_TG_PLAY);
            }
        }
=======
    protected synchronized A2dpSinkStateMachine getStateMachine(BluetoothDevice device) {
        return mDeviceStateMap.get(device);
>>>>>>> e94de994c16aa83274259da3c313a88d57b26b48
    }

    /**
     * Request audio focus such that the designated device can stream audio
     */
    public void requestAudioFocus(BluetoothDevice device, boolean request) {
<<<<<<< HEAD
        A2dpSinkStateMachine sm = null;
        synchronized (mBtA2dpLock) {
            sm = mStateMachines.get(device);
            if (sm == null) {
                return;
            }
        }
        sm.sendMessage(A2dpSinkStateMachine.EVENT_REQUEST_FOCUS);
    }

    synchronized boolean isA2dpPlaying(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        if (DBG) {
            Log.d(TAG, "isA2dpPlaying(" + device + ")");
        }
        synchronized (mBtA2dpLock) {
            A2dpSinkStateMachine mStateMachine = mStateMachines.get(device);
            if (mStateMachine == null) {
                return false;
            }
        }
        return mStateMachine.isPlaying(device);
    }

    BluetoothAudioConfig getAudioConfig(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        A2dpSinkStateMachine sm = null;
        synchronized (mBtA2dpLock) {
            sm = mStateMachines.get(device);
            if (sm == null) {
                return null;
            }
        }
        return sm.getAudioConfig(device);
=======
        mA2dpSinkStreamHandler.requestAudioFocus(request);
    }

    @Override
    protected IProfileServiceBinder initBinder() {
        return new A2dpSinkServiceBinder(this);
>>>>>>> e94de994c16aa83274259da3c313a88d57b26b48
    }

    //Binder object: Must be static class or memory leak may occur
    private static class A2dpSinkServiceBinder extends IBluetoothA2dpSink.Stub
            implements IProfileServiceBinder {
        private A2dpSinkService mService;

        private A2dpSinkService getService() {
            if (!Utils.checkCaller()) {
                Log.w(TAG, "A2dp call not allowed for non-active user");
                return null;
            }

            if (mService != null) {
                return mService;
            }
            return null;
        }

        A2dpSinkServiceBinder(A2dpSinkService svc) {
            mService = svc;
        }

        @Override
        public void cleanup() {
            mService = null;
        }

        @Override
        public boolean connect(BluetoothDevice device) {
            A2dpSinkService service = getService();
            if (service == null) {
                return false;
            }
            return service.connect(device);
        }

        @Override
        public boolean disconnect(BluetoothDevice device) {
            A2dpSinkService service = getService();
            if (service == null) {
                return false;
            }
            return service.disconnect(device);
        }

        @Override
        public List<BluetoothDevice> getConnectedDevices() {
            A2dpSinkService service = getService();
            if (service == null) {
                return new ArrayList<BluetoothDevice>(0);
            }
            return service.getConnectedDevices();
        }

        @Override
        public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
            A2dpSinkService service = getService();
            if (service == null) {
                return new ArrayList<BluetoothDevice>(0);
            }
            return service.getDevicesMatchingConnectionStates(states);
        }

        @Override
        public int getConnectionState(BluetoothDevice device) {
            A2dpSinkService service = getService();
            if (service == null) {
                return BluetoothProfile.STATE_DISCONNECTED;
            }
            return service.getConnectionState(device);
        }

        @Override
        public boolean setPriority(BluetoothDevice device, int priority) {
            A2dpSinkService service = getService();
            if (service == null) {
                return false;
            }
            return service.setPriority(device, priority);
        }

        @Override
        public int getPriority(BluetoothDevice device) {
            A2dpSinkService service = getService();
            if (service == null) {
                return BluetoothProfile.PRIORITY_UNDEFINED;
            }
            return service.getPriority(device);
        }

        @Override
        public boolean isA2dpPlaying(BluetoothDevice device) {
            A2dpSinkService service = getService();
            if (service == null) {
                return false;
            }
            return service.isA2dpPlaying(device);
        }

        @Override
        public BluetoothAudioConfig getAudioConfig(BluetoothDevice device) {
            A2dpSinkService service = getService();
            if (service == null) {
                return null;
            }
            return service.getAudioConfig(device);
        }
    }

    /* Generic Profile Code */

    /**
     * Connect the given Bluetooth device.
     *
     * @return true if connection is successful, false otherwise.
     */
    public synchronized boolean connect(BluetoothDevice device) {
        if (device == null) {
            throw new IllegalArgumentException("Null device");
        }
        if (DBG) {
            StringBuilder sb = new StringBuilder();
            dump(sb);
            Log.d(TAG, " connect device: " + device
                    + ", InstanceMap start state: " + sb.toString());
        }
        A2dpSinkStateMachine stateMachine = getOrCreateStateMachine(device);
        if (stateMachine != null) {
            stateMachine.connect();
            return true;
        } else {
            // a state machine instance doesn't exist yet, and the max has been reached.
            Log.e(TAG, "Maxed out on the number of allowed MAP connections. "
                    + "Connect request rejected on " + device);
            return false;

        }
    }

    /**
     * Disconnect the given Bluetooth device.
     *
     * @return true if disconnect is successful, false otherwise.
     */
    public synchronized boolean disconnect(BluetoothDevice device) {
        if (DBG) {
            StringBuilder sb = new StringBuilder();
            dump(sb);
            Log.d(TAG, "A2DP disconnect device: " + device
                    + ", InstanceMap start state: " + sb.toString());
        }
        A2dpSinkStateMachine stateMachine = mDeviceStateMap.get(device);
        // a state machine instance doesn't exist. maybe it is already gone?
        if (stateMachine == null) {
            return false;
        }
        int connectionState = stateMachine.getState();
        if (connectionState == BluetoothProfile.STATE_DISCONNECTED
                || connectionState == BluetoothProfile.STATE_DISCONNECTING) {
            return false;
        }
        // upon completion of disconnect, the state machine will remove itself from the available
        // devices map
        stateMachine.disconnect();
        return true;
    }

    void removeStateMachine(A2dpSinkStateMachine stateMachine) {
        mDeviceStateMap.remove(stateMachine.getDevice());
    }

    public List<BluetoothDevice> getConnectedDevices() {
        return getDevicesMatchingConnectionStates(new int[]{BluetoothAdapter.STATE_CONNECTED});
    }

    protected A2dpSinkStateMachine getOrCreateStateMachine(BluetoothDevice device) {
        A2dpSinkStateMachine stateMachine = mDeviceStateMap.get(device);
        if (stateMachine == null) {
            stateMachine = newStateMachine(device);
            mDeviceStateMap.put(device, stateMachine);
            stateMachine.start();
        }
        return stateMachine;
    }

    List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        if (DBG) Log.d(TAG, "getDevicesMatchingConnectionStates" + Arrays.toString(states));
        List<BluetoothDevice> deviceList = new ArrayList<>();
        Set<BluetoothDevice> bondedDevices = mAdapter.getBondedDevices();
        int connectionState;
        for (BluetoothDevice device : bondedDevices) {
            connectionState = getConnectionState(device);
            if (DBG) Log.d(TAG, "Device: " + device + "State: " + connectionState);
            for (int i = 0; i < states.length; i++) {
                if (connectionState == states[i]) {
                    deviceList.add(device);
                }
            }
        }
        if (DBG) Log.d(TAG, deviceList.toString());
        Log.d(TAG, "GetDevicesDone");
        return deviceList;
    }

    synchronized int getConnectionState(BluetoothDevice device) {
        A2dpSinkStateMachine stateMachine = mDeviceStateMap.get(device);
        return (stateMachine == null) ? BluetoothProfile.STATE_DISCONNECTED
                : stateMachine.getState();
    }

    /**
     * Set the priority of the  profile.
     *
     * @param device   the remote device
     * @param priority the priority of the profile
     * @return true on success, otherwise false
     */
    public boolean setPriority(BluetoothDevice device, int priority) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH_ADMIN permission");
        if (DBG) {
            Log.d(TAG, "Saved priority " + device + " = " + priority);
        }
        AdapterService.getAdapterService().getDatabase()
                .setProfilePriority(device, BluetoothProfile.A2DP_SINK, priority);
        return true;
    }

    /**
     * Get the priority of the profile.
     *
     * @param device the remote device
     * @return priority of the specified device
     */
    public int getPriority(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH_ADMIN permission");
        return AdapterService.getAdapterService().getDatabase()
                .getProfilePriority(device, BluetoothProfile.A2DP_SINK);
    }


    @Override
    public void dump(StringBuilder sb) {
        super.dump(sb);
        ProfileService.println(sb, "Devices Tracked = " + mDeviceStateMap.size());
        for (A2dpSinkStateMachine stateMachine : mDeviceStateMap.values()) {
            ProfileService.println(sb,
                    "==== StateMachine for " + stateMachine.getDevice() + " ====");
            stateMachine.dump(sb);
        }
    }

    /**
     * Get the current Bluetooth Audio focus state
     *
     * @return focus
     */
    public static int getFocusState() {
        return sService.mA2dpSinkStreamHandler.getFocusState();
    }

    boolean isA2dpPlaying(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        return mA2dpSinkStreamHandler.isPlaying();
    }

    BluetoothAudioConfig getAudioConfig(BluetoothDevice device) {
        A2dpSinkStateMachine stateMachine = mDeviceStateMap.get(device);
        // a state machine instance doesn't exist. maybe it is already gone?
        if (stateMachine == null) {
            return null;
        }
        return stateMachine.getAudioConfig();
    }

    /* JNI interfaces*/

    private static native void classInitNative();

    private native void initNative();

    private native void cleanupNative();

    native boolean connectA2dpNative(byte[] address);

    native boolean disconnectA2dpNative(byte[] address);

    /**
     * inform A2DP decoder of the current audio focus
     *
     * @param focusGranted
     */
    @VisibleForTesting
    public native void informAudioFocusStateNative(int focusGranted);

    /**
     * inform A2DP decoder the desired audio gain
     *
     * @param gain
     */
    @VisibleForTesting
    public native void informAudioTrackGainNative(float gain);

    private void onConnectionStateChanged(byte[] address, int state) {
        StackEvent event = StackEvent.connectionStateChanged(getDevice(address), state);
        A2dpSinkStateMachine stateMachine = getOrCreateStateMachine(event.mDevice);
        stateMachine.sendMessage(A2dpSinkStateMachine.STACK_EVENT, event);
    }

    private void onAudioStateChanged(byte[] address, int state) {
        if (state == StackEvent.AUDIO_STATE_STARTED) {
            mA2dpSinkStreamHandler.obtainMessage(
                    A2dpSinkStreamHandler.SRC_STR_START).sendToTarget();
        } else if (state == StackEvent.AUDIO_STATE_STOPPED) {
            mA2dpSinkStreamHandler.obtainMessage(
                    A2dpSinkStreamHandler.SRC_STR_STOP).sendToTarget();
        }
    }

<<<<<<< HEAD
    /* Get Number of connected/connecting devices*/
    public List<BluetoothDevice> getConnectedDevices() {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        synchronized (mBtA2dpLock) {
            List<BluetoothDevice> devices = new ArrayList<>();
            for (A2dpSinkStateMachine sm : mStateMachines.values()) {
                if (sm.isConnected()) {
                    devices.add(sm.getDevice());
                }
            }
            return devices;
        }
    }

    /* This API returns existing state machine for remote device or creates new if not present.*/
    private A2dpSinkStateMachine getOrCreateStateMachine(BluetoothDevice device) {
        if (device == null) {
            Log.e(TAG, "getOrCreateStateMachine failed: device cannot be null");
            return null;
        }
        synchronized (mBtA2dpLock) {
            A2dpSinkStateMachine sm = mStateMachines.get(device);
            if (sm != null) {
                Log.i(TAG, "Return existing state machine for device:" + device);
                return sm;
            }
            // Limit the maximum number of state machines to avoid DoS
            if (mStateMachines.size() >= mMaxA2dpSinkConnections) {
                Log.e(TAG, "Maximum number of A2DP Sink Connections reached: "
                        + mMaxA2dpSinkConnections);
                return null;
            }
            if (DBG) {
                Log.d(TAG, "Creating a new state machine for " + device);
            }

            sm = A2dpSinkStateMachine.make(this, mStateMachinesThread.getLooper(), device);
            mStateMachines.put(device, sm);
            return sm;
        }
    }

    public static BluetoothDevice getCurrentStreamingDevice() {
        return mStreamingDevice;
    }

    /* This API performs all the operations required for doing soft-Handoff */
    public synchronized void initiateHandoffOperations(BluetoothDevice device) {
        if (mStreamingDevice != null && !mStreamingDevice.equals(device)) {
           Log.d(TAG, "Soft-Handoff. Prev Device:" + mStreamingDevice + ", New: " + device);

           for (A2dpSinkStateMachine otherSm: mStateMachines.values()) {
               BluetoothDevice otherDevice = otherSm.getDevice();
               if (mStreamingDevice.equals(otherDevice)) {
                   Log.d(TAG, "Release Audio Focus for " + otherDevice);
                   otherSm.sendMessage(A2dpSinkStateMachine.EVENT_RELEASE_FOCUS);
                   // Send Passthrough Command for PAUSE
                   AvrcpControllerService avrcpService =
                           AvrcpControllerService.getAvrcpControllerService();
                   avrcpService.sendPassThroughCmd(mStreamingDevice,
                       AvrcpControllerService.PASS_THRU_CMD_ID_PAUSE,
                       AvrcpControllerService.KEY_STATE_PRESSED);
                   avrcpService.sendPassThroughCmd(mStreamingDevice,
                       AvrcpControllerService.PASS_THRU_CMD_ID_PAUSE,
                       AvrcpControllerService.KEY_STATE_RELEASED);

                   // send intent for updated streaming device so that media session is updated
                   Intent intent = new Intent(BluetoothMediaBrowserService.ACTION_DEVICE_UPDATED);
                   intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
                   sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);

                   /* set autoconnect priority of non-streaming device to PRIORITY_ON and priority
                    *  of streaming device to PRIORITY_AUTO_CONNECT */
                   setPriority(otherDevice, BluetoothProfile.PRIORITY_ON);
                   setPriority(device, BluetoothProfile.PRIORITY_AUTO_CONNECT);
                   break;
               }
           }
       } else if (mStreamingDevice == null && device != null) {
           Log.d(TAG, "Prev Device: Null. New Streaming Device: " + device);
           // No Action Required
       }
    }

    /* JNI Changes for SINK SHO */
    private void onConnectionStateChanged(byte[] address, int state) {
        BluetoothDevice device = getDevice(address);
        Log.d(TAG, "onConnectionStateChanged. State = " + state + ", device:" + device
                + ", streaming:" + mStreamingDevice);
        A2dpSinkStateMachine sm = getOrCreateStateMachine(device);
        if (sm == null || device == null) {
            Log.e(TAG, "State Machine not found for device:" + device + ". Return.");
            return;
        }

        // If streaming device is disconnected, release audio focus and update mStreamingDevice
        if (state == BluetoothProfile.STATE_DISCONNECTED && device.equals(mStreamingDevice)) {
            Log.d(TAG, "Release Audio Focus for Streaming device: " + device);
            sm.sendMessage(A2dpSinkStateMachine.EVENT_RELEASE_FOCUS);
            mStreamingDevice = null;
        }

        // Intiate Handoff operations when state has been connectiond
        if (state == BluetoothProfile.STATE_CONNECTED) {
            if (mStreamingDevice != null && !mStreamingDevice.equals(device)) {
                Log.d(TAG, "current connected device: " + device + "is different from previous device");
                initiateHandoffOperations(device);
                mStreamingDevice = device;
            } else if (device != null) {
                mStreamingDevice = device;
            }
        }

        A2dpSinkStateMachine.StackEvent event =
                sm.new StackEvent(A2dpSinkStateMachine.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        event.device = device;
        event.valueInt = state;
        sm.sendMessage(A2dpSinkStateMachine.STACK_EVENT, event);
    }

    private void onAudioStateChanged(byte[] address, int state) {
        BluetoothDevice device = getDevice(address);
        Log.d(TAG, "onAudioStateChanged. Audio State = " + state + ", device:" + device);
        A2dpSinkStateMachine sm = mStateMachines.get(device);
        if (sm == null) {
            return;
        }

        // Intiate Handoff operations if AUDIO_STATE_STARTED for other connected device
        if (state == A2dpSinkStateMachine.AUDIO_STATE_STARTED) {
            initiateHandoffOperations(device);
            mStreamingDevice = device; // mark playing device as streaming device
        }

        A2dpSinkStateMachine.StackEvent event =
                sm.new StackEvent(A2dpSinkStateMachine.EVENT_TYPE_AUDIO_STATE_CHANGED);
        event.device = device;
        event.valueInt = state;
        sm.sendMessage(A2dpSinkStateMachine.STACK_EVENT, event);
    }

    private void onAudioConfigChanged(byte[] address, int sampleRate, int channelCount) {
        BluetoothDevice device = getDevice(address);
        Log.d(TAG, "onAudioConfigChanged:- device:" + device + " samplerate:" + sampleRate
                + ", channelCount:" + channelCount);
        A2dpSinkStateMachine sm = mStateMachines.get(device);
        if (sm == null) {
            return;
        }

        A2dpSinkStateMachine.StackEvent event =
                sm.new StackEvent(A2dpSinkStateMachine.EVENT_TYPE_AUDIO_CONFIG_CHANGED);
        event.device = device;
        int channelConfig =
                (channelCount == 1 ? AudioFormat.CHANNEL_IN_MONO : AudioFormat.CHANNEL_IN_STEREO);
        event.audioConfig =
                new BluetoothAudioConfig(sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT);
        sm.sendMessage(A2dpSinkStateMachine.STACK_EVENT, event);
    }

    private static native void classInitNative();

    private native void initNative();

    private native void cleanupNative();

    public static native boolean connectA2dpNative(byte[] address);

    public static native boolean disconnectA2dpNative(byte[] address);

    public static native void informAudioFocusStateNative(int focusGranted);

    public static native void informAudioTrackGainNative(float focusGranted);

=======
    private void onAudioConfigChanged(byte[] address, int sampleRate, int channelCount) {
        StackEvent event = StackEvent.audioConfigChanged(getDevice(address), sampleRate,
                channelCount);
        A2dpSinkStateMachine stateMachine = getOrCreateStateMachine(event.mDevice);
        stateMachine.sendMessage(A2dpSinkStateMachine.STACK_EVENT, event);
    }
>>>>>>> e94de994c16aa83274259da3c313a88d57b26b48
}
