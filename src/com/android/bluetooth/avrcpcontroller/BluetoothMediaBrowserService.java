/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.bluetooth.avrcpcontroller;

import android.media.MediaMetadata;
import android.media.browse.MediaBrowser.MediaItem;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.service.media.MediaBrowserService;
import android.util.Log;

import com.android.bluetooth.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Implements the MediaBrowserService interface to AVRCP and A2DP
 *
 * This service provides a means for external applications to access A2DP and AVRCP.
 * The applications are expected to use MediaBrowser (see API) and all the music
 * browsing/playback/metadata can be controlled via MediaBrowser and MediaController.
 *
 * The current behavior of MediaSession exposed by this service is as follows:
 * 1. MediaSession is active (i.e. SystemUI and other overview UIs can see updates) when device is
 * connected and first starts playing. Before it starts playing we do not active the session.
 * 1.1 The session is active throughout the duration of connection.
 * 2. The session is de-activated when the device disconnects. It will be connected again when (1)
 * happens.
 */
public class BluetoothMediaBrowserService extends MediaBrowserService {
    private static final String TAG = "BluetoothMediaBrowserService";
<<<<<<< HEAD
    private static final boolean DBG = false;
    private static final boolean VDBG = false;

    private static final String UNKNOWN_BT_AUDIO = "__UNKNOWN_BT_AUDIO__";
    private static final float PLAYBACK_SPEED = 1.0f;

    // Message sent when A2DP device is disconnected.
    private static final int MSG_DEVICE_DISCONNECT = 0;
    // Message sent when A2DP device is connected.
    private static final int MSG_DEVICE_CONNECT = 2;
    // Message sent when we recieve a TRACK update from AVRCP profile over a connected A2DP device.
    private static final int MSG_TRACK = 4;
    // Internal message sent to trigger a AVRCP action.
    private static final int MSG_AVRCP_PASSTHRU = 5;
    // Internal message to trigger a getplaystatus command to remote.
    private static final int MSG_AVRCP_GET_PLAY_STATUS_NATIVE = 6;
    // Message sent when AVRCP browse is connected.
    private static final int MSG_DEVICE_BROWSE_CONNECT = 7;
    // Message sent when AVRCP browse is disconnected.
    private static final int MSG_DEVICE_BROWSE_DISCONNECT = 8;
    // Message sent when folder list is fetched.
    private static final int MSG_FOLDER_LIST = 9;
    // Message sent when streaming device is updated after soft-handoff
    private static final int MSG_DEVICE_UPDATED = 10;

    // Custom actions for PTS testing.
    private static final String CUSTOM_ACTION_VOL_UP =
            "com.android.bluetooth.avrcpcontroller.CUSTOM_ACTION_VOL_UP";
    private static final String CUSTOM_ACTION_VOL_DN =
            "com.android.bluetooth.avrcpcontroller.CUSTOM_ACTION_VOL_DN";
    private static final String CUSTOM_ACTION_GET_PLAY_STATUS_NATIVE =
            "com.android.bluetooth.avrcpcontroller.CUSTOM_ACTION_GET_PLAY_STATUS_NATIVE";
=======
    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);
>>>>>>> e94de994c16aa83274259da3c313a88d57b26b48

    private static BluetoothMediaBrowserService sBluetoothMediaBrowserService;

    private MediaSession mSession;

    // Browsing related structures.
    private List<MediaSession.QueueItem> mMediaQueue = new ArrayList<>();

<<<<<<< HEAD
    public static final String ACTION_DEVICE_UPDATED =
            "android.bluetooth.a2dp.mbs.action.DeviceUpdated";

    private static final class AvrcpCommandQueueHandler extends Handler {
        WeakReference<BluetoothMediaBrowserService> mInst;

        AvrcpCommandQueueHandler(Looper looper, BluetoothMediaBrowserService sink) {
            super(looper);
            mInst = new WeakReference<BluetoothMediaBrowserService>(sink);
        }

        @Override
        public void handleMessage(Message msg) {
            BluetoothMediaBrowserService inst = mInst.get();
            if (inst == null) {
                Log.e(TAG, "Parent class has died; aborting.");
                return;
            }

            switch (msg.what) {
                case MSG_DEVICE_CONNECT:
                    inst.msgDeviceConnect((BluetoothDevice) msg.obj);
                    break;
                case MSG_DEVICE_DISCONNECT:
                    inst.msgDeviceDisconnect((BluetoothDevice) msg.obj);
                    break;
                case MSG_TRACK:
                    Pair<PlaybackState, MediaMetadata> pair =
                            (Pair<PlaybackState, MediaMetadata>) (msg.obj);
                    inst.msgTrack(pair.first, pair.second);
                    break;
                case MSG_AVRCP_PASSTHRU:
                    inst.msgPassThru((int) msg.obj);
                    break;
                case MSG_AVRCP_GET_PLAY_STATUS_NATIVE:
                    inst.msgGetPlayStatusNative();
                    break;
                case MSG_DEVICE_BROWSE_CONNECT:
                    inst.msgDeviceBrowseConnect((BluetoothDevice) msg.obj);
                    break;
                case MSG_DEVICE_BROWSE_DISCONNECT:
                    inst.msgDeviceBrowseDisconnect((BluetoothDevice) msg.obj);
                    break;
                case MSG_FOLDER_LIST:
                    inst.msgFolderList((Intent) msg.obj);
                    break;
                case MSG_DEVICE_UPDATED:
                    inst.msgDeviceUpdated((BluetoothDevice) msg.obj);
                default:
                    Log.e(TAG, "Message not handled " + msg);
            }
        }
    }

=======
>>>>>>> e94de994c16aa83274259da3c313a88d57b26b48
    /**
     * Initialize this BluetoothMediaBrowserService, creating our MediaSession, MediaPlayer and
     * MediaMetaData, and setting up mechanisms to talk with the AvrcpControllerService.
     */
    @Override
    public void onCreate() {
        if (DBG) Log.d(TAG, "onCreate");
        super.onCreate();

        // Create and configure the MediaSession
        mSession = new MediaSession(this, TAG);
        setSessionToken(mSession.getSessionToken());
        mSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mSession.setQueueTitle(getString(R.string.bluetooth_a2dp_sink_queue_name));
        mSession.setQueue(mMediaQueue);
<<<<<<< HEAD

        // Create and setup the MediaPlayer
        initMediaPlayer();

        // Associate the held MediaSession with this browser and activate it
        setSessionToken(mSession.getSessionToken());
        mSession.setActive(true);

        // Internal handler to process events and requests
        mAvrcpCommandQueue = new AvrcpCommandQueueHandler(Looper.getMainLooper(), this);

        // Set the initial Media state (sets current playback state and media meta data)
        refreshInitialPlayingState();

        // Set up communication with the controller service
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAvrcpController.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(AvrcpControllerService.ACTION_BROWSE_CONNECTION_STATE_CHANGED);
        filter.addAction(AvrcpControllerService.ACTION_TRACK_EVENT);
        filter.addAction(AvrcpControllerService.ACTION_FOLDER_LIST);
        filter.addAction(BluetoothMediaBrowserService.ACTION_DEVICE_UPDATED);
        registerReceiver(mBtReceiver, filter);

        synchronized (this) {
            mParentIdToRequestMap.clear();
        }

        setBluetoothMediaBrowserService(this);
    }

    /**
     * Clean up this instance in the reverse order that we created it.
     */
    @Override
    public void onDestroy() {
        if (DBG) Log.d(TAG, "onDestroy");
        setBluetoothMediaBrowserService(null);
        unregisterReceiver(mBtReceiver);
        destroyMediaPlayer();
        mSession.release();
        super.onDestroy();
    }

    /**
     * Initializes the silent MediaPlayer object which aids in receiving media key focus.
     *
     * The created MediaPlayer is already prepared and will release and stop itself on error. All
     * you need to do is start() it.
     */
    private void initMediaPlayer() {
        if (DBG) Log.d(TAG, "initMediaPlayer()");

        // Parameters for create
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build();
        AudioManager am = getSystemService(AudioManager.class);

        // Create our player object. Returns a prepared player on success, null on failure
        mMediaPlayer = MediaPlayer.create(this, R.raw.silent, attrs, am.generateAudioSessionId());
        if (mMediaPlayer == null) {
            Log.e(TAG, "Failed to initialize media player. You may not get media key events");
            return;
        }

        // Set other player attributes
        mMediaPlayer.setLooping(false);
        mMediaPlayer.setOnErrorListener((mp, what, extra) -> {
            Log.e(TAG, "Silent media player error: " + what + ", " + extra);
            destroyMediaPlayer();
            return false;
        });
    }

    /**
     * Safely tears down our local MediaPlayer
     */
    private void destroyMediaPlayer() {
        if (DBG) Log.d(TAG, "destroyMediaPlayer()");
        if (mMediaPlayer == null) {
            return;
        }
        mMediaPlayer.stop();
        mMediaPlayer.release();
        mMediaPlayer = null;
    }

    /**
     * Uses the internal MediaPlayer to play a silent, short audio sample so that AudioService will
     * treat us as the active MediaSession/MediaPlayer combo and properly route us media key events.
     *
     * If the MediaPlayer failed to initialize properly, this call will fail gracefully and log the
     * failed attempt. Media keys will not be routed.
     */
    private void getMediaKeyFocus() {
        if (DBG) Log.d(TAG, "getMediaKeyFocus()");
        if (mMediaPlayer == null) {
            Log.w(TAG, "Media player is null. Can't get media key focus. Media keys may not route");
            return;
        }
        mMediaPlayer.start();
=======
        sBluetoothMediaBrowserService = this;
>>>>>>> e94de994c16aa83274259da3c313a88d57b26b48
    }

    List<MediaItem> getContents(final String parentMediaId) {
        AvrcpControllerService avrcpControllerService =
                AvrcpControllerService.getAvrcpControllerService();
        if (avrcpControllerService == null) {
            return new ArrayList(0);
        } else {
            return avrcpControllerService.getContents(parentMediaId);
        }
    }

    @Override
    public synchronized void onLoadChildren(final String parentMediaId,
            final Result<List<MediaItem>> result) {
        if (DBG) Log.d(TAG, "onLoadChildren parentMediaId=" + parentMediaId);
        List<MediaItem> contents = getContents(parentMediaId);
        if (contents == null) {
            result.detach();
        } else {
            result.sendResult(contents);
        }
    }

    @Override
<<<<<<< HEAD
    public void onLoadItem(String itemId, Result<MediaBrowser.MediaItem> result) {
    }

    // Media Session Stuff.
    private MediaSession.Callback mSessionCallbacks = new MediaSession.Callback() {
        @Override
        public void onPlay() {
            if (DBG) Log.d(TAG, "onPlay");
            mAvrcpCommandQueue.obtainMessage(MSG_AVRCP_PASSTHRU,
                    AvrcpControllerService.PASS_THRU_CMD_ID_PLAY).sendToTarget();
            // TRACK_EVENT should be fired eventually and the UI should be hence updated.
        }

        @Override
        public void onPause() {
            if (DBG) Log.d(TAG, "onPause");
            mAvrcpCommandQueue.obtainMessage(MSG_AVRCP_PASSTHRU,
                    AvrcpControllerService.PASS_THRU_CMD_ID_PAUSE).sendToTarget();
            // TRACK_EVENT should be fired eventually and the UI should be hence updated.
        }

        @Override
        public void onSkipToNext() {
            if (DBG) Log.d(TAG, "onSkipToNext");
            mAvrcpCommandQueue.obtainMessage(MSG_AVRCP_PASSTHRU,
                    AvrcpControllerService.PASS_THRU_CMD_ID_FORWARD).sendToTarget();
            // TRACK_EVENT should be fired eventually and the UI should be hence updated.
        }

        @Override
        public void onSkipToPrevious() {
            if (DBG) Log.d(TAG, "onSkipToPrevious");
            mAvrcpCommandQueue.obtainMessage(MSG_AVRCP_PASSTHRU,
                    AvrcpControllerService.PASS_THRU_CMD_ID_BACKWARD).sendToTarget();
            // TRACK_EVENT should be fired eventually and the UI should be hence updated.
        }

        @Override
        public void onSkipToQueueItem(long id) {
            if (DBG) Log.d(TAG, "onSkipToQueueItem" + id);
            if (mA2dpSinkService != null) {
                mA2dpSinkService.requestAudioFocus(mA2dpDevice, true);
            }
            MediaSession.QueueItem queueItem = mMediaQueue.get((int) id);
            if (queueItem != null) {
                String mediaId = queueItem.getDescription().getMediaId();
                mAvrcpCtrlSrvc.fetchAttrAndPlayItem(mA2dpDevice, mediaId);
            }
        }

        @Override
        public void onStop() {
            if (DBG) Log.d(TAG, "onStop");
            mAvrcpCommandQueue.obtainMessage(MSG_AVRCP_PASSTHRU,
                    AvrcpControllerService.PASS_THRU_CMD_ID_STOP).sendToTarget();
        }

        @Override
        public void onPrepare() {
            if (DBG) Log.d(TAG, "onPrepare");
            if (mA2dpSinkService != null) {
                mA2dpSinkService.requestAudioFocus(mA2dpDevice, true);
                getMediaKeyFocus();
            }
        }

        @Override
        public void onRewind() {
            if (DBG) Log.d(TAG, "onRewind");
            mAvrcpCommandQueue.obtainMessage(MSG_AVRCP_PASSTHRU,
                    AvrcpControllerService.PASS_THRU_CMD_ID_REWIND).sendToTarget();
            // TRACK_EVENT should be fired eventually and the UI should be hence updated.
        }

        @Override
        public void onFastForward() {
            if (DBG) Log.d(TAG, "onFastForward");
            mAvrcpCommandQueue.obtainMessage(MSG_AVRCP_PASSTHRU,
                    AvrcpControllerService.PASS_THRU_CMD_ID_FF).sendToTarget();
            // TRACK_EVENT should be fired eventually and the UI should be hence updated.
        }

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            synchronized (BluetoothMediaBrowserService.this) {
                // Play the item if possible.
                if (mA2dpSinkService != null) {
                    mA2dpSinkService.requestAudioFocus(mA2dpDevice, true);
                    getMediaKeyFocus();
                }
                mAvrcpCtrlSrvc.fetchAttrAndPlayItem(mA2dpDevice, mediaId);
            }

            // TRACK_EVENT should be fired eventually and the UI should be hence updated.
        }

        // Support VOL UP and VOL DOWN events for PTS testing.
        @Override
        public void onCustomAction(String action, Bundle extras) {
            if (DBG) Log.d(TAG, "onCustomAction " + action);
            if (CUSTOM_ACTION_VOL_UP.equals(action)) {
                mAvrcpCommandQueue.obtainMessage(MSG_AVRCP_PASSTHRU,
                        AvrcpControllerService.PASS_THRU_CMD_ID_VOL_UP).sendToTarget();
            } else if (CUSTOM_ACTION_VOL_DN.equals(action)) {
                mAvrcpCommandQueue.obtainMessage(MSG_AVRCP_PASSTHRU,
                        AvrcpControllerService.PASS_THRU_CMD_ID_VOL_DOWN).sendToTarget();
            } else if (CUSTOM_ACTION_GET_PLAY_STATUS_NATIVE.equals(action)) {
                mAvrcpCommandQueue.obtainMessage(MSG_AVRCP_GET_PLAY_STATUS_NATIVE).sendToTarget();
            } else {
                Log.w(TAG, "Custom action " + action + " not supported.");
            }
        }
    };

    private BroadcastReceiver mBtReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DBG) Log.d(TAG, "onReceive intent=" + intent);
            String action = intent.getAction();
            BluetoothDevice btDev =
                    (BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);

            if (BluetoothAvrcpController.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
                if (DBG) {
                    Log.d(TAG, "handleConnectionStateChange: newState="
                            + state + " btDev=" + btDev);
                }

                // Connected state will be handled when AVRCP BluetoothProfile gets connected.
                if (state == BluetoothProfile.STATE_CONNECTED) {
                    mAvrcpCommandQueue.obtainMessage(MSG_DEVICE_CONNECT, btDev).sendToTarget();
                } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                    // Set the playback state to unconnected.
                    mAvrcpCommandQueue.obtainMessage(MSG_DEVICE_DISCONNECT, btDev).sendToTarget();
                    // If we have been pushing updates via the session then stop sending them since
                    // we are not connected anymore.
                    if (mSession.isActive()) {
                        mSession.setActive(false);
                    }
                }
            } else if (AvrcpControllerService.ACTION_BROWSE_CONNECTION_STATE_CHANGED.equals(
                    action)) {
                if (state == BluetoothProfile.STATE_CONNECTED) {
                    mAvrcpCommandQueue.obtainMessage(MSG_DEVICE_BROWSE_CONNECT, btDev)
                            .sendToTarget();
                } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                    mAvrcpCommandQueue.obtainMessage(MSG_DEVICE_BROWSE_DISCONNECT, btDev)
                            .sendToTarget();
                }
            } else if (AvrcpControllerService.ACTION_TRACK_EVENT.equals(action)) {
                PlaybackState pbb =
                        intent.getParcelableExtra(AvrcpControllerService.EXTRA_PLAYBACK);
                MediaMetadata mmd =
                        intent.getParcelableExtra(AvrcpControllerService.EXTRA_METADATA);
                mAvrcpCommandQueue.obtainMessage(MSG_TRACK,
                        new Pair<PlaybackState, MediaMetadata>(pbb, mmd)).sendToTarget();
            } else if (AvrcpControllerService.ACTION_FOLDER_LIST.equals(action)) {
                mAvrcpCommandQueue.obtainMessage(MSG_FOLDER_LIST, intent).sendToTarget();
            } else if (ACTION_DEVICE_UPDATED.equals(action)) {
                 mAvrcpCommandQueue.obtainMessage(MSG_DEVICE_UPDATED, btDev).sendToTarget();
            }
        }
    };

    private synchronized void msgDeviceConnect(BluetoothDevice device) {
        if (DBG) Log.d(TAG, "msgDeviceConnect");
        // We are connected to a new device via A2DP now.
        mA2dpDevice = device;
        mAvrcpCtrlSrvc = AvrcpControllerService.getAvrcpControllerService();
        if (mAvrcpCtrlSrvc == null) {
            Log.e(TAG, "!!!AVRCP Controller cannot be null");
            return;
        }
        refreshInitialPlayingState();
    }

    private synchronized void msgDeviceUpdated(BluetoothDevice device) {
        if (device != null && device.equals(mA2dpDevice)) {
            return;
        }
        Log.d(TAG, "msgDeviceUpdated. Previous: " + mA2dpDevice + " New: " + device);
        // We are connected to a new device via A2DP now.
        mA2dpDevice = device;
        mAvrcpCtrlSrvc = AvrcpControllerService.getAvrcpControllerService();
        if (mAvrcpCtrlSrvc == null) {
            Log.e(TAG, "!!!AVRCP Controller cannot be null");
            return;
        }
        if (mAvrcpCtrlSrvc.isBrowsingConnected(device))
            notifyChildrenChanged("__ROOT__");
        refreshInitialPlayingState();
    }

    // Refresh the UI if we have a connected device and AVRCP is initialized.
    private synchronized void refreshInitialPlayingState() {
        if (mA2dpDevice == null) {
            if (DBG) Log.d(TAG, "device " + mA2dpDevice);
            return;
        }

        List<BluetoothDevice> devices = mAvrcpCtrlSrvc.getConnectedDevices();
        if (devices.size() == 0) {
            Log.w(TAG, "No devices connected yet");
            return;
        }

        if (mA2dpDevice != null && !devices.contains(mA2dpDevice)) {
            Log.w(TAG, "A2dp device : " + mA2dpDevice + " avrcp device " + devices.get(0));
            return;
        }
        mA2dpSinkService = A2dpSinkService.getA2dpSinkService();

        PlaybackState playbackState = mAvrcpCtrlSrvc.getPlaybackState(mA2dpDevice);
        MediaMetadata mediaMetadata = mAvrcpCtrlSrvc.getMetaData(mA2dpDevice);
        if (VDBG) {
            Log.d(TAG, "Media metadata " + mediaMetadata + " playback state " + playbackState);
        }
        mSession.setMetadata(mAvrcpCtrlSrvc.getMetaData(mA2dpDevice));
        mSession.setPlaybackState(playbackState);
=======
    public BrowserRoot onGetRoot(String clientPackageName, int clientUid, Bundle rootHints) {
        if (DBG) Log.d(TAG, "onGetRoot");
        return new BrowserRoot(BrowseTree.ROOT, null);
>>>>>>> e94de994c16aa83274259da3c313a88d57b26b48
    }

    private void updateNowPlayingQueue(BrowseTree.BrowseNode node) {
        List<MediaItem> songList = node.getContents();
        mMediaQueue.clear();
        if (songList != null) {
            for (MediaItem song : songList) {
                mMediaQueue.add(new MediaSession.QueueItem(song.getDescription(),
                        mMediaQueue.size()));
            }
        }
        mSession.setQueue(mMediaQueue);
    }

    static synchronized void notifyChanged(BrowseTree.BrowseNode node) {
        if (sBluetoothMediaBrowserService != null) {
            if (node.getScope() == AvrcpControllerService.BROWSE_SCOPE_NOW_PLAYING) {
                sBluetoothMediaBrowserService.updateNowPlayingQueue(node);
            } else {
                sBluetoothMediaBrowserService.notifyChildrenChanged(node.getID());
            }
        }
    }

    static synchronized void addressedPlayerChanged(MediaSession.Callback callback) {
        if (sBluetoothMediaBrowserService != null) {
            sBluetoothMediaBrowserService.mSession.setCallback(callback);
        } else {
            Log.w(TAG, "addressedPlayerChanged Unavailable");
        }
    }

    static synchronized void trackChanged(MediaMetadata mediaMetadata) {
        if (sBluetoothMediaBrowserService != null) {
            sBluetoothMediaBrowserService.mSession.setMetadata(mediaMetadata);
        } else {
            Log.w(TAG, "trackChanged Unavailable");
        }
    }

    static synchronized void notifyChanged(PlaybackState playbackState) {
        Log.d(TAG, "notifyChanged PlaybackState" + playbackState);
        if (sBluetoothMediaBrowserService != null) {
            sBluetoothMediaBrowserService.mSession.setPlaybackState(playbackState);
        } else {
            Log.w(TAG, "notifyChanged Unavailable");
        }
    }

    /**
     * Send AVRCP Play command
     */
    public static synchronized void play() {
        if (sBluetoothMediaBrowserService != null) {
            sBluetoothMediaBrowserService.mSession.getController().getTransportControls().play();
        } else {
            Log.w(TAG, "play Unavailable");
        }
    }

    /**
     * Send AVRCP Pause command
     */
    public static synchronized void pause() {
        if (sBluetoothMediaBrowserService != null) {
            sBluetoothMediaBrowserService.mSession.getController().getTransportControls().pause();
        } else {
            Log.w(TAG, "pause Unavailable");
        }
    }

    /**
     * Get object for controlling playback
     */
    public static synchronized MediaController.TransportControls getTransportControls() {
        if (sBluetoothMediaBrowserService != null) {
            return sBluetoothMediaBrowserService.mSession.getController().getTransportControls();
        } else {
            Log.w(TAG, "transportControls Unavailable");
            return null;
        }
    }

    /**
     * Set Media session active whenever we have Focus of any kind
     */
    public static synchronized void setActive(boolean active) {
        if (sBluetoothMediaBrowserService != null) {
            sBluetoothMediaBrowserService.mSession.setActive(active);
        } else {
            Log.w(TAG, "setActive Unavailable");
        }
    }
}
