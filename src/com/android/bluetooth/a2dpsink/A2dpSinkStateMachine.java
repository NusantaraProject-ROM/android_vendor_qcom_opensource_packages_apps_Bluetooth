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

import android.bluetooth.BluetoothA2dpSink;
import android.bluetooth.BluetoothAudioConfig;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.media.AudioFormat;
<<<<<<< HEAD
import android.media.AudioManager;
import android.media.AudioSystem;
import android.os.Handler;
import android.os.Looper;
=======
>>>>>>> e94de994c16aa83274259da3c313a88d57b26b48
import android.os.Message;
import android.util.Log;

import com.android.bluetooth.BluetoothMetricsProto;
import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.MetricsLogger;
import com.android.bluetooth.btservice.ProfileService;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;


public class A2dpSinkStateMachine extends StateMachine {
<<<<<<< HEAD
    private static final boolean DBG = false;

    static final int CONNECT = 1;
    static final int DISCONNECT = 2;
    protected static final int STACK_EVENT = 101;
    private static final int CONNECT_TIMEOUT = 201;
    public static final int EVENT_AVRCP_CT_PLAY = 301;
    public static final int EVENT_AVRCP_CT_PAUSE = 302;
    public static final int EVENT_AVRCP_TG_PLAY = 303;
    public static final int EVENT_AVRCP_TG_PAUSE = 304;
    public static final int EVENT_REQUEST_FOCUS = 305;
    public static final int EVENT_RELEASE_FOCUS = 306;

    private static final int IS_INVALID_DEVICE = 0;
    private static final int IS_VALID_DEVICE = 1;
    private static final int CONNECT_TIMEOUT_MS = 5000;
    public static final int AVRC_ID_PLAY = 0x44;
    public static final int AVRC_ID_PAUSE = 0x46;
    public static final int KEY_STATE_PRESSED = 0;
    public static final int KEY_STATE_RELEASED = 1;
    private static final String BT_ADDR_KEY = "bt_addr";

    // Connection states.
    // 1. Disconnected: The connection does not exist.
    // 2. Pending: The connection is being established.
    // 3. Connected: The connection is established. The audio connection is in Idle state.
    private Disconnected mDisconnected;
    private Connecting mConnecting;
    private Disconnecting mDisconnecting;
    private Connected mConnected;

    private A2dpSinkService mService;
    private Context mContext;
    private BluetoothAdapter mAdapter;

    private static final int MSG_CONNECTION_STATE_CHANGED = 0;

    private final Object mLockForPatch = new Object();

    private A2dpSinkStreamHandler mStreaming = null;

    private final BluetoothDevice mDevice;
    private static final String TAG = "A2dpSinkStateMachine";
    private int mConnectionState = BluetoothProfile.STATE_DISCONNECTED;
    private boolean mIsPlaying = false;
    private Looper mLooper;
    private final HashMap<BluetoothDevice, BluetoothAudioConfig> mAudioConfigs =
            new HashMap<BluetoothDevice, BluetoothAudioConfig>();
    private int mLastConnectionState = -1;

    private A2dpSinkStateMachine(A2dpSinkService svc, Looper looper, BluetoothDevice device) {
        super(TAG, looper);
        mService = svc;
        mContext = svc;
        mLooper = looper;
        mDevice = device;
        mAdapter = BluetoothAdapter.getDefaultAdapter();

        if (Looper.myLooper() == null)
            Looper.prepare();
=======
    static final String TAG = "A2DPSinkStateMachine";
    static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);

    //0->99 Events from Outside
    public static final int CONNECT = 1;
    public static final int DISCONNECT = 2;

    //100->199 Internal Events
    protected static final int CLEANUP = 100;
    private static final int CONNECT_TIMEOUT = 101;

    //200->299 Events from Native
    static final int STACK_EVENT = 200;

    static final int CONNECT_TIMEOUT_MS = 5000;

    protected final BluetoothDevice mDevice;
    protected final byte[] mDeviceAddress;
    protected final A2dpSinkService mService;
    protected final Disconnected mDisconnected;
    protected final Connecting mConnecting;
    protected final Connected mConnected;
    protected final Disconnecting mDisconnecting;

    protected int mMostRecentState = BluetoothProfile.STATE_DISCONNECTED;
    protected BluetoothAudioConfig mAudioConfig = null;

    A2dpSinkStateMachine(BluetoothDevice device, A2dpSinkService service) {
        super(TAG);
        mDevice = device;
        mDeviceAddress = Utils.getByteAddress(mDevice);
        mService = service;
        if (DBG) Log.d(TAG, device.toString());
>>>>>>> e94de994c16aa83274259da3c313a88d57b26b48

        mDisconnected = new Disconnected();
        mConnecting = new Connecting();
        mConnected = new Connected();
        mDisconnecting = new Disconnecting();

        addState(mDisconnected);
        addState(mConnecting);
        addState(mConnected);
        addState(mDisconnecting);

<<<<<<< HEAD
        if (mAdapter != null) {
            String bdAddr = mAdapter.getAddress();
            AudioSystem.setParameters(BT_ADDR_KEY + "=" + bdAddr);
            log("AudioSystem.setParameters, Key: " + BT_ADDR_KEY + " Value: " + bdAddr);
        }

        setInitialState(mDisconnected);

        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
    }

    static A2dpSinkStateMachine make(A2dpSinkService svc, Looper looper, BluetoothDevice device) {
        Log.d("A2dpSinkStateMachine", "make");
        A2dpSinkStateMachine a2dpSm = new A2dpSinkStateMachine(svc, looper, device);
        a2dpSm.start();
        return a2dpSm;
    }

    public void doQuit() {
        if (DBG) {
            Log.d("A2dpSinkStateMachine", "Quit");
        }
        if (mIsPlaying) {
            mIsPlaying = false;
            broadcastAudioState(mDevice, BluetoothA2dpSink.STATE_NOT_PLAYING,
                                BluetoothA2dpSink.STATE_PLAYING);
        }
        synchronized (A2dpSinkStateMachine.this) {
            mStreaming = null;
        }
        quitNow();
    }

    public void cleanup() {
        mAudioConfigs.clear();
=======
        setInitialState(mDisconnected);
    }

    protected String getConnectionStateChangedIntent() {
        return BluetoothA2dpSink.ACTION_CONNECTION_STATE_CHANGED;
    }

    /**
     * Get the current connection state
     *
     * @return current State
     */
    public int getState() {
        return mMostRecentState;
    }

    /**
     * get current audio config
     */
    BluetoothAudioConfig getAudioConfig() {
        return mAudioConfig;
    }

    /**
     * Get the underlying device tracked by this state machine
     *
     * @return device in focus
     */
    public synchronized BluetoothDevice getDevice() {
        return mDevice;
    }

    /**
     * send the Connect command asynchronously
     */
    public final void connect() {
        sendMessage(CONNECT);
>>>>>>> e94de994c16aa83274259da3c313a88d57b26b48
    }

    /**
     * send the Disconnect command asynchronously
     */
    public final void disconnect() {
        sendMessage(DISCONNECT);
    }

    /**
     * Dump the current State Machine to the string builder.
     * @param sb output string
     */
    public void dump(StringBuilder sb) {
<<<<<<< HEAD
        ProfileService.println(sb, "mDevice: " + mDevice);
        ProfileService.println(sb, "StateMachine: " + this.toString());
        ProfileService.println(sb, "isPlaying: " + mIsPlaying);
=======
        ProfileService.println(sb, "mDevice: " + mDevice.getAddress() + "("
                + mDevice.getName() + ") " + this.toString());
    }

    @Override
    protected void unhandledMessage(Message msg) {
        Log.w(TAG, "unhandledMessage in state " + getCurrentState() + "msg.what=" + msg.what);
>>>>>>> e94de994c16aa83274259da3c313a88d57b26b48
    }

    class Disconnected extends State {
        @Override
        public void enter() {
<<<<<<< HEAD
            log("Enter Disconnected (Device: " + mDevice + ") : " + getCurrentMessage().what);
            mConnectionState = BluetoothProfile.STATE_DISCONNECTED;
            if (mIsPlaying) {
                Log.i(TAG, "Disconnected: stopped playing: " + mDevice);
                mIsPlaying = false;
                broadcastAudioState(mDevice, BluetoothA2dpSink.STATE_NOT_PLAYING,
                                BluetoothA2dpSink.STATE_PLAYING);
            }
            if (mLastConnectionState != -1) {
                log("Quit State Machine for device:" + mDevice);
                broadcastConnectionState(mDevice, mConnectionState, mLastConnectionState);
                mService.removeStateMachine(mDevice);
            }
=======
            if (DBG) Log.d(TAG, "Enter Disconnected");
            if (mMostRecentState != BluetoothProfile.STATE_DISCONNECTED) {
                sendMessage(CLEANUP);
            }
            onConnectionStateChanged(BluetoothProfile.STATE_DISCONNECTED);
>>>>>>> e94de994c16aa83274259da3c313a88d57b26b48
        }

        @Override
        public boolean processMessage(Message message) {
<<<<<<< HEAD
            log("Disconnected (Device: " + mDevice + ") Process Message: " + message.what);

            boolean retValue = HANDLED;
            switch (message.what) {
                case CONNECT:
                    if (mDevice == null) {
                        Log.e(TAG, "State Machine for Null Device Reference, Return.");
                        return NOT_HANDLED;
                    }

                    if (!A2dpSinkService.connectA2dpNative(getByteAddress(mDevice))) {
                        break;
                    }

                    synchronized (A2dpSinkStateMachine.this) {
                        transitionTo(mConnecting);
                    }
                    break;
                case DISCONNECT:
                    // ignore
                    break;
=======
            switch (message.what) {
>>>>>>> e94de994c16aa83274259da3c313a88d57b26b48
                case STACK_EVENT:
                    processStackEvent((StackEvent) message.obj);
                    return true;
                case CONNECT:
                    if (DBG) Log.d(TAG, "Connect");
                    transitionTo(mConnecting);
                    return true;
                case CLEANUP:
                    mService.removeStateMachine(A2dpSinkStateMachine.this);
                    return true;
            }
<<<<<<< HEAD
            return retValue;
        }

        @Override
        public void exit() {
            log("Exit Disconnected: (" + mDevice + "): " + getCurrentMessage().what);
            mLastConnectionState = BluetoothProfile.STATE_DISCONNECTED;
        }

        // in Disconnected state
        private void processConnectionEvent(BluetoothDevice device, int state) {
            switch (state) {
                case CONNECTION_STATE_DISCONNECTED:
                    logw("Device: " + device + " already in disconnected state. Ignore ");
                    break;
                case CONNECTION_STATE_CONNECTING:
                    if (okToConnect(device)) {
                        logi("Incoming A2DP accepted");
                        synchronized (A2dpSinkStateMachine.this) {
                            transitionTo(mConnecting);
                        }
                    } else {
                        //reject the connection and stay in Disconnected state itself
                        logi("Incoming A2DP rejected");
                        A2dpSinkService.disconnectA2dpNative(getByteAddress(device));
                    }
                    break;
                case CONNECTION_STATE_CONNECTED:
                    logw("A2DP Connected from Disconnected state");
                    if (okToConnect(device)) {
                        logi("Incoming A2DP accepted");
                        synchronized (A2dpSinkStateMachine.this) {
                            transitionTo(mConnected);
                        }
                    } else {
                        //reject the connection and stay in Disconnected state itself
                        logi("Incoming A2DP rejected");
                        A2dpSinkService.disconnectA2dpNative(getByteAddress(device));
=======
            return false;
        }

        void processStackEvent(StackEvent event) {
            switch (event.mType) {
                case StackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED:
                    switch (event.mState) {
                        case StackEvent.CONNECTION_STATE_CONNECTED:
                            transitionTo(mConnected);
                            break;
                        case StackEvent.CONNECTION_STATE_DISCONNECTED:
                            sendMessage(CLEANUP);
                            break;
>>>>>>> e94de994c16aa83274259da3c313a88d57b26b48
                    }
            }
        }
    }

<<<<<<< HEAD
    private class Connecting extends State {
        @Override
        public void enter() {
            log("Enter CONNECTING: " + getCurrentMessage().what);
            mConnectionState = BluetoothProfile.STATE_CONNECTING;
            broadcastConnectionState(mDevice, mConnectionState, mLastConnectionState);
        }

        @Override
        public void exit() {
            log("Exit CONNECTING: " + getCurrentMessage().what);
            mLastConnectionState = BluetoothProfile.STATE_CONNECTING;
=======
    class Connecting extends State {
        boolean mIncommingConnection = false;

        @Override
        public void enter() {
            if (DBG) Log.d(TAG, "Enter Connecting");
            onConnectionStateChanged(BluetoothProfile.STATE_CONNECTING);
            sendMessageDelayed(CONNECT_TIMEOUT, CONNECT_TIMEOUT_MS);

            if (!mIncommingConnection) {
                mService.connectA2dpNative(mDeviceAddress);
            }

            super.enter();
>>>>>>> e94de994c16aa83274259da3c313a88d57b26b48
        }

        @Override
        public boolean processMessage(Message message) {
<<<<<<< HEAD
            log("SM STATE: CONNECTING (" + mDevice + ") process message: " + message.what);

            boolean retValue = HANDLED;
            switch (message.what) {
                case CONNECT:
                    logd("Connection is already in pregress.");
                    break;
                case CONNECT_TIMEOUT:
                    onConnectionStateChanged(getByteAddress(mDevice),
                                             CONNECTION_STATE_DISCONNECTED);
                    break;
                case DISCONNECT:
                    if (mDevice == null) {
                        Log.e(TAG, "Message received for wrong device.");
                        return NOT_HANDLED;
                    }
                    // cancel connection to the mDevice
                    A2dpSinkService.disconnectA2dpNative(getByteAddress(mDevice)); //
                    transitionTo(mDisconnected);
                    break;
=======
            switch (message.what) {
>>>>>>> e94de994c16aa83274259da3c313a88d57b26b48
                case STACK_EVENT:
                    processStackEvent((StackEvent) message.obj);
                    return true;
                case CONNECT_TIMEOUT:
                    transitionTo(mDisconnected);
                    return true;
            }
            return false;
        }

        void processStackEvent(StackEvent event) {
            switch (event.mType) {
                case StackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED:
                    switch (event.mState) {
                        case StackEvent.CONNECTION_STATE_CONNECTED:
                            transitionTo(mConnected);
                            break;
                        case StackEvent.CONNECTION_STATE_DISCONNECTED:
                            transitionTo(mDisconnected);
                            break;
                    }
            }
        }
<<<<<<< HEAD

        // in Pending state
        private void processConnectionEvent(BluetoothDevice device, int state) {
            log("processConnectionEvent state " + state);
            switch (state) {
                case CONNECTION_STATE_DISCONNECTED:
                    // connection failed
                    mAudioConfigs.remove(device);
                    synchronized (A2dpSinkStateMachine.this) {
                        transitionTo(mDisconnected);
                    }
                    break;
                case CONNECTION_STATE_CONNECTED:
                    // Connection completed
                    synchronized (A2dpSinkStateMachine.this) {
                        transitionTo(mConnected);
                    }
                    break;
                case CONNECTION_STATE_CONNECTING:
                    log("current device tries to connect back. Ignore");
                    break;
                case CONNECTION_STATE_DISCONNECTING:
                    // remote trying to disconnect
                    if (DBG) {
                        log("stack is disconnecting mDevice");
                    }
                    transitionTo(mDisconnecting);
                    break;
                default:
                    loge("Incorrect state: " + state);
                    break;
            }
        }
    }

    private class Disconnecting extends State {
        @Override
        public void enter() {
            log("Enter Disconnecting: " + getCurrentMessage().what);
            mConnectionState = BluetoothProfile.STATE_DISCONNECTING;
            broadcastConnectionState(mDevice, mConnectionState, mLastConnectionState);
        }

=======
>>>>>>> e94de994c16aa83274259da3c313a88d57b26b48
        @Override
        public boolean processMessage(Message message) {
            log("DISCONNECTING (" + mDevice + ") process message: " + message.what);

            boolean retValue = HANDLED;
            switch (message.what) {
                case CONNECT:
                    logd("Disconnection is in progress. Try again later.");
                    break;
                case CONNECT_TIMEOUT:
                    onConnectionStateChanged(getByteAddress(mDevice),
                                             CONNECTION_STATE_DISCONNECTED);
                    break;
                case DISCONNECT:
                    Log.e(TAG, "Message received for wrong device.");
                    break;

                case EVENT_RELEASE_FOCUS:
                    mStreaming.obtainMessage(A2dpSinkStreamHandler.RELEASE_FOCUS).sendToTarget();
                    break;

                case STACK_EVENT:
                    StackEvent event = (StackEvent) message.obj;
                    log("STACK_EVENT " + event.type);
                    switch (event.type) {
                        case EVENT_TYPE_CONNECTION_STATE_CHANGED:
                            processConnectionEvent(event.device, event.valueInt);
                            break;
                        case EVENT_TYPE_AUDIO_CONFIG_CHANGED:
                            processAudioConfigEvent(event.device, event.audioConfig);
                            break;
                        default:
                            loge("Unexpected stack event: " + event.type);
                            break;
                    }
                    break;
                default:
                    return NOT_HANDLED;
            }
            return retValue;
        }

<<<<<<< HEAD
        // in Pending state
        private void processConnectionEvent(BluetoothDevice device, int state) {
            log("processConnectionEvent state " + state);
            switch (state) {
                case CONNECTION_STATE_DISCONNECTED:
                    mAudioConfigs.remove(device);
                    synchronized (A2dpSinkStateMachine.this) {
                        transitionTo(mDisconnected);
                    }
                    break;
                case CONNECTION_STATE_CONNECTED:
                    // disconnection failed
                    synchronized (A2dpSinkStateMachine.this) {
                        transitionTo(mConnected);
                    }
                    break;
                case CONNECTION_STATE_CONNECTING:
                    log("Current device tries to connect back.");
                    transitionTo(mConnecting);
                    break;
                case CONNECTION_STATE_DISCONNECTING:
                    // we already broadcasted the intent, doing nothing here
                    if (DBG) {
                        log("stack is disconnecting mDevice. Ignore.");
                    }
                    break;
                default:
                    loge("Incorrect state: " + state);
                    break;
            }
        }

        @Override
        public void exit() {
            log("Exit Disconnecting: (" + mDevice + "): " + getCurrentMessage().what);
            mLastConnectionState = BluetoothProfile.STATE_DISCONNECTING;
        }
=======
>>>>>>> e94de994c16aa83274259da3c313a88d57b26b48
    }

    class Connected extends State {
        @Override
        public void enter() {
<<<<<<< HEAD
            log("Enter Connected (Device: "+ mDevice + ") " + getCurrentMessage().what);
            // Upon connected, the audio starts out as stopped
            mConnectionState = BluetoothProfile.STATE_CONNECTED;
            broadcastAudioState(mDevice, BluetoothA2dpSink.STATE_NOT_PLAYING,
                    BluetoothA2dpSink.STATE_PLAYING);
            broadcastConnectionState(mDevice, mConnectionState, mLastConnectionState);
            synchronized (A2dpSinkStateMachine.this) {
                if (mStreaming == null) {
                    if (DBG) {
                        log("Creating New A2dpSinkStreamHandler");
                    }
                    mStreaming = new A2dpSinkStreamHandler(A2dpSinkStateMachine.this,
                            mContext, mDevice);
                }
            }
            if (mStreaming.getAudioFocus() == AudioManager.AUDIOFOCUS_NONE) {
                A2dpSinkService.informAudioFocusStateNative(0);
            }
=======
            if (DBG) Log.d(TAG, "Enter Connected");
            onConnectionStateChanged(BluetoothProfile.STATE_CONNECTED);
>>>>>>> e94de994c16aa83274259da3c313a88d57b26b48
        }

        @Override
        public void exit() {
            log("Exit Connected: (" + mDevice + "): " + getCurrentMessage().what);
            mLastConnectionState = BluetoothProfile.STATE_CONNECTED;
        }

        @Override
        public boolean processMessage(Message message) {
<<<<<<< HEAD
            log("Connected (Device: "+ mDevice + ") process message: " + message.what);
            if (mDevice == null) {
                loge("ERROR: Current Device is null in Connected");
                return NOT_HANDLED;
            }

            switch (message.what) {
                case CONNECT:
                    logd("Connect received in Connected State");
                break;

                case DISCONNECT: {
                    if (!A2dpSinkService.disconnectA2dpNative(getByteAddress(mDevice))) {
                        break;
                    }
                    mStreaming.obtainMessage(A2dpSinkStreamHandler.DISCONNECT).sendToTarget();
                    transitionTo(mDisconnecting);
                }
                break;

                case STACK_EVENT:
                    StackEvent event = (StackEvent) message.obj;
                    switch (event.type) {
                        case EVENT_TYPE_CONNECTION_STATE_CHANGED:
                            processConnectionEvent(event.device, event.valueInt);
                            break;
                        case EVENT_TYPE_AUDIO_STATE_CHANGED:
                            processAudioStateEvent(event.device, event.valueInt);
                            break;
                        case EVENT_TYPE_AUDIO_CONFIG_CHANGED:
                            processAudioConfigEvent(event.device, event.audioConfig);
                            break;
                        default:
                            loge("Unexpected stack event: " + event.type);
                            break;
                    }
                    break;

                case EVENT_AVRCP_CT_PLAY:
                    mStreaming.obtainMessage(A2dpSinkStreamHandler.SNK_PLAY).sendToTarget();
                    break;

                case EVENT_AVRCP_TG_PLAY:
                    mStreaming.obtainMessage(A2dpSinkStreamHandler.SRC_PLAY).sendToTarget();
                    break;

                case EVENT_AVRCP_CT_PAUSE:
                    mStreaming.obtainMessage(A2dpSinkStreamHandler.SNK_PAUSE).sendToTarget();
                    break;

                case EVENT_AVRCP_TG_PAUSE:
                    mStreaming.obtainMessage(A2dpSinkStreamHandler.SRC_PAUSE).sendToTarget();
                    break;

                case EVENT_REQUEST_FOCUS:
                    mStreaming.obtainMessage(A2dpSinkStreamHandler.REQUEST_FOCUS).sendToTarget();
                    break;

                case EVENT_RELEASE_FOCUS:
                    mStreaming.obtainMessage(A2dpSinkStreamHandler.RELEASE_FOCUS).sendToTarget();
                    break;

                default:
                    return NOT_HANDLED;
=======
            switch (message.what) {
                case DISCONNECT:
                    transitionTo(mDisconnecting);
                    mService.disconnectA2dpNative(mDeviceAddress);
                    return true;
                case STACK_EVENT:
                    processStackEvent((StackEvent) message.obj);
                    return true;
>>>>>>> e94de994c16aa83274259da3c313a88d57b26b48
            }
            return false;
        }

<<<<<<< HEAD
        // in Connected state
        private void processConnectionEvent(BluetoothDevice device, int state) {
            if (mDevice != null && device != null && !mDevice.equals(device)) {
                Log.e(TAG, "Message received for wrong device.");
                return;
            }
            switch (state) {
                case CONNECTION_STATE_DISCONNECTED:
                    mAudioConfigs.remove(device);
                    if (mDevice.equals(device)) {
                        synchronized (A2dpSinkStateMachine.this) {
                            // Take care of existing audio focus in the streaming state machine.
                            mStreaming.obtainMessage(A2dpSinkStreamHandler.DISCONNECT)
                                    .sendToTarget();
                            transitionTo(mDisconnected);
                        }
                    } else {
                        loge("Disconnected from unknown device: " + device);
                    }
                    break;
                default:
                    loge("Connection State Device: " + device + " bad state: " + state);
                    break;
            }
        }

        private void processAudioStateEvent(BluetoothDevice device, int state) {
            if (!mDevice.equals(device)) {
                loge("Audio State Device:" + device + "is different from ConnectedDevice:"
                        + mDevice);
                return;
            }
            log(" processAudioStateEvent in state " + state);
            switch (state) {
                case AUDIO_STATE_STARTED:
                    mIsPlaying = true;
                    mStreaming.obtainMessage(A2dpSinkStreamHandler.SRC_STR_START).sendToTarget();
                    broadcastAudioState(device, BluetoothA2dpSink.STATE_PLAYING,
                            BluetoothA2dpSink.STATE_NOT_PLAYING);
                    break;
                case AUDIO_STATE_REMOTE_SUSPEND:
                case AUDIO_STATE_STOPPED:
                    mIsPlaying = false;
                    if (mDevice.equals(A2dpSinkService.mStreamingDevice)) {
                        mStreaming.obtainMessage(A2dpSinkStreamHandler.SRC_STR_STOP).sendToTarget();
                    } else {
                        Log.d(TAG, "other than streaming device. Ignore");
=======
        void processStackEvent(StackEvent event) {
            switch (event.mType) {
                case StackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED:
                    switch (event.mState) {
                        case StackEvent.CONNECTION_STATE_DISCONNECTING:
                            transitionTo(mDisconnecting);
                            break;
                        case StackEvent.CONNECTION_STATE_DISCONNECTED:
                            transitionTo(mDisconnected);
                            break;
>>>>>>> e94de994c16aa83274259da3c313a88d57b26b48
                    }
                    broadcastAudioState(device, BluetoothA2dpSink.STATE_NOT_PLAYING,
                            BluetoothA2dpSink.STATE_PLAYING);
                    break;
                case StackEvent.EVENT_TYPE_AUDIO_CONFIG_CHANGED:
                    mAudioConfig = new BluetoothAudioConfig(event.mSampleRate, event.mChannelCount,
                            AudioFormat.ENCODING_PCM_16BIT);
                    break;
            }
        }
    }

<<<<<<< HEAD
    private void processAudioConfigEvent(BluetoothDevice device, BluetoothAudioConfig audioConfig) {
        log("processAudioConfigEvent: " + device);
        mAudioConfigs.put(device, audioConfig);
        broadcastAudioConfig(device, audioConfig);
    }

    int getConnectionState() {
        return mConnectionState;
    }

    BluetoothAudioConfig getAudioConfig(BluetoothDevice device) {
        return mAudioConfigs.get(device);
    }

    List<BluetoothDevice> getConnectedDevices() {
        List<BluetoothDevice> devices = new ArrayList<BluetoothDevice>();
        synchronized (this) {
            if (getCurrentState() == mConnected) {
                devices.add(mDevice);
            }
        }
        return devices;
    }

    boolean isPlaying(BluetoothDevice device) {
        synchronized (this) {
            if ((mDevice != null) && (device.equals(mDevice))) {
                return mIsPlaying;
            }
        }
        return false;
    }

    boolean isConnected() {
        synchronized (this) {
            return (getCurrentState() == mConnected);
        }
    }

    // Utility Functions
    boolean okToConnect(BluetoothDevice device) {
        AdapterService adapterService = AdapterService.getAdapterService();
        int priority = mService.getPriority(device);

        // check priority and accept or reject the connection. if priority is undefined
        // it is likely that our SDP has not completed and peer is initiating the
        // connection. Allow this connection, provided the device is bonded
        if ((BluetoothProfile.PRIORITY_OFF < priority) || (
                (BluetoothProfile.PRIORITY_UNDEFINED == priority) && (device.getBondState()
                        != BluetoothDevice.BOND_NONE))) {
            return true;
        }
        logw("okToConnect not OK to connect " + device);
        return false;
    }

    private void broadcastConnectionState(BluetoothDevice device, int newState, int prevState) {
        if (prevState != newState && newState == BluetoothProfile.STATE_CONNECTED) {
            MetricsLogger.logProfileConnectionEvent(BluetoothMetricsProto.ProfileId.A2DP_SINK);
        }
        Intent intent = new Intent(BluetoothA2dpSink.ACTION_CONNECTION_STATE_CHANGED);
        intent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, prevState);
        intent.putExtra(BluetoothProfile.EXTRA_STATE, newState);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
//FIXME            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        mContext.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
        Log.d(TAG, "Connection state " + device + ": " + prevState + "->" + newState);
    }

    private void broadcastAudioState(BluetoothDevice device, int state, int prevState) {
        Intent intent = new Intent(BluetoothA2dpSink.ACTION_PLAYING_STATE_CHANGED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, prevState);
        intent.putExtra(BluetoothProfile.EXTRA_STATE, state);
//FIXME        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        mContext.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);

        log("A2DP Playing state : device: " + device + " State:" + prevState + "->" + state);
    }

    private void broadcastAudioConfig(BluetoothDevice device, BluetoothAudioConfig audioConfig) {
        Intent intent = new Intent(BluetoothA2dpSink.ACTION_AUDIO_CONFIG_CHANGED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.putExtra(BluetoothA2dpSink.EXTRA_AUDIO_CONFIG, audioConfig);
//FIXME        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        mContext.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);

        log("A2DP Audio Config : device: " + device + " config: " + audioConfig);
    }

    private byte[] getByteAddress(BluetoothDevice device) {
        return Utils.getBytesFromAddress(device.getAddress());
    }

    private void onConnectionStateChanged(byte[] address, int state) {
        StackEvent event = new StackEvent(EVENT_TYPE_CONNECTION_STATE_CHANGED);
        event.device = getDevice(address);
        event.valueInt = state;
        sendMessage(STACK_EVENT, event);
    }

    private void onAudioStateChanged(byte[] address, int state) {
        StackEvent event = new StackEvent(EVENT_TYPE_AUDIO_STATE_CHANGED);
        event.device = getDevice(address);
        event.valueInt = state;
        sendMessage(STACK_EVENT, event);
    }

    private void onAudioConfigChanged(byte[] address, int sampleRate, int channelCount) {
        StackEvent event = new StackEvent(EVENT_TYPE_AUDIO_CONFIG_CHANGED);
        event.device = getDevice(address);
        int channelConfig =
                (channelCount == 1 ? AudioFormat.CHANNEL_IN_MONO : AudioFormat.CHANNEL_IN_STEREO);
        event.audioConfig =
                new BluetoothAudioConfig(sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT);
        sendMessage(STACK_EVENT, event);
    }

    private BluetoothDevice getDevice(byte[] address) {
        return mAdapter.getRemoteDevice(Utils.getAddressStringFromByte(address));
    }

    public class StackEvent {
        public int type = EVENT_TYPE_NONE;
        public BluetoothDevice device = null;
        public int valueInt = 0;
        public BluetoothAudioConfig audioConfig = null;

        public StackEvent(int type) {
            this.type = type;
        }
    }

    public boolean sendPassThruPlay(BluetoothDevice mDevice) {
        log("sendPassThruPlay + ");
        AvrcpControllerService avrcpCtrlService =
                AvrcpControllerService.getAvrcpControllerService();
        if ((avrcpCtrlService != null) && (mDevice != null)
                && (avrcpCtrlService.getConnectedDevices().contains(mDevice))) {
            avrcpCtrlService.sendPassThroughCmd(mDevice,
                    AvrcpControllerService.PASS_THRU_CMD_ID_PLAY,
                    AvrcpControllerService.KEY_STATE_PRESSED);
            avrcpCtrlService.sendPassThroughCmd(mDevice,
                    AvrcpControllerService.PASS_THRU_CMD_ID_PLAY,
                    AvrcpControllerService.KEY_STATE_RELEASED);
            log(" sendPassThruPlay command sent - ");
            return true;
        } else {
            log("passthru command not sent, connection unavailable");
            return false;
=======
    protected class Disconnecting extends State {
        @Override
        public void enter() {
            if (DBG) Log.d(TAG, "Enter Disconnecting");
            onConnectionStateChanged(BluetoothProfile.STATE_DISCONNECTING);
            transitionTo(mDisconnected);
        }
    }

    protected void onConnectionStateChanged(int currentState) {
        if (mMostRecentState == currentState) {
            return;
        }
        if (currentState == BluetoothProfile.STATE_CONNECTED) {
            MetricsLogger.logProfileConnectionEvent(BluetoothMetricsProto.ProfileId.A2DP_SINK);
        }
        if (DBG) {
            Log.d(TAG, "Connection state " + mDevice + ": " + mMostRecentState + "->"
                    + currentState);
>>>>>>> e94de994c16aa83274259da3c313a88d57b26b48
        }
        Intent intent = new Intent(BluetoothA2dpSink.ACTION_CONNECTION_STATE_CHANGED);
        intent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, mMostRecentState);
        intent.putExtra(BluetoothProfile.EXTRA_STATE, currentState);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mDevice);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        mMostRecentState = currentState;
        mService.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
    }
<<<<<<< HEAD

    BluetoothDevice getDevice() {
        return mDevice;
    }

    // Event types for STACK_EVENT message
    protected static final int EVENT_TYPE_NONE = 0;
    protected static final int EVENT_TYPE_CONNECTION_STATE_CHANGED = 1;
    protected static final int EVENT_TYPE_AUDIO_STATE_CHANGED = 2;
    protected static final int EVENT_TYPE_AUDIO_CONFIG_CHANGED = 3;

    // Do not modify without updating the HAL bt_av.h files.

    // match up with btav_connection_state_t enum of bt_av.h
    static final int CONNECTION_STATE_DISCONNECTED = 0;
    static final int CONNECTION_STATE_CONNECTING = 1;
    static final int CONNECTION_STATE_CONNECTED = 2;
    static final int CONNECTION_STATE_DISCONNECTING = 3;

    // match up with btav_audio_state_t enum of bt_av.h
    static final int AUDIO_STATE_REMOTE_SUSPEND = 0;
    static final int AUDIO_STATE_STOPPED = 1;
    static final int AUDIO_STATE_STARTED = 2;

=======
>>>>>>> e94de994c16aa83274259da3c313a88d57b26b48
}
