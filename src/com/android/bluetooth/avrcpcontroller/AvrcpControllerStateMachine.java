/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.bluetooth.BluetoothAvrcpController;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.browse.MediaBrowser.MediaItem;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;
import android.util.SparseArray;

import com.android.bluetooth.BluetoothMetricsProto;
import com.android.bluetooth.R;
import com.android.bluetooth.Utils;
import com.android.bluetooth.a2dpsink.A2dpSinkService;
import com.android.bluetooth.btservice.MetricsLogger;
import com.android.bluetooth.btservice.ProfileService;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;

import java.util.ArrayList;
import java.util.List;
/**
 * Provides Bluetooth AVRCP Controller State Machine responsible for all remote control connections
 * and interactions with a remote controlable device.
 */
class AvrcpControllerStateMachine extends StateMachine {
    static final String TAG = "AvrcpControllerStateMachine";
    static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);

    //0->99 Events from Outside
    public static final int CONNECT = 1;
    public static final int DISCONNECT = 2;

    //100->199 Internal Events
    protected static final int CLEANUP = 100;
    private static final int CONNECT_TIMEOUT = 101;

    //200->299 Events from Native
    static final int STACK_EVENT = 200;
    static final int MESSAGE_INTERNAL_CMD_TIMEOUT = 201;

    static final int MESSAGE_PROCESS_SET_ABS_VOL_CMD = 203;
    static final int MESSAGE_PROCESS_REGISTER_ABS_VOL_NOTIFICATION = 204;
    static final int MESSAGE_PROCESS_TRACK_CHANGED = 205;
    static final int MESSAGE_PROCESS_PLAY_POS_CHANGED = 206;
    static final int MESSAGE_PROCESS_PLAY_STATUS_CHANGED = 207;
    static final int MESSAGE_PROCESS_VOLUME_CHANGED_NOTIFICATION = 208;
    static final int MESSAGE_PROCESS_GET_FOLDER_ITEMS = 209;
    static final int MESSAGE_PROCESS_GET_FOLDER_ITEMS_OUT_OF_RANGE = 210;
    static final int MESSAGE_PROCESS_GET_PLAYER_ITEMS = 211;
    static final int MESSAGE_PROCESS_FOLDER_PATH = 212;
    static final int MESSAGE_PROCESS_SET_BROWSED_PLAYER = 213;
    static final int MESSAGE_PROCESS_SET_ADDRESSED_PLAYER = 214;
    static final int MESSAGE_PROCESS_ADDRESSED_PLAYER_CHANGED = 215;
    static final int MESSAGE_PROCESS_NOW_PLAYING_CONTENTS_CHANGED = 216;

    //300->399 Events for Browsing
    static final int MESSAGE_GET_FOLDER_ITEMS = 300;
    static final int MESSAGE_PLAY_ITEM = 301;
    static final int MSG_AVRCP_PASSTHRU = 302;

    static final int MESSAGE_INTERNAL_ABS_VOL_TIMEOUT = 404;

    /*
     * Base value for absolute volume from JNI
     */
    private static final int ABS_VOL_BASE = 127;

<<<<<<< HEAD
    /*
     * Notification types for Avrcp protocol JNI.
     */
    private static final byte NOTIFICATION_RSP_TYPE_INTERIM = 0x00;
    private static final byte NOTIFICATION_RSP_TYPE_CHANGED = 0x01;


    private static final String TAG = "AvrcpControllerSM";
    private static final boolean DBG = true;
    private static final boolean VDBG = AvrcpControllerService.VDBG;

    private final Context mContext;
    private final AudioManager mAudioManager;
    private AvrcpControllerBipStateMachine mBipStateMachine;
    private static CoverArtUtils mCoveArtUtils;
    private final State mDisconnected;
    private final State mConnected;
    private final SetAddresedPlayerAndPlayItem mSetAddrPlayer;
    private final GetFolderList mGetFolderList;

    private final Object mLock = new Object();
    private static final MediaMetadata EMPTY_MEDIA_METADATA = new MediaMetadata.Builder().build();
=======
    private final AudioManager mAudioManager;

    protected final BluetoothDevice mDevice;
    protected final byte[] mDeviceAddress;
    protected final AvrcpControllerService mService;
    protected final Disconnected mDisconnected;
    protected final Connecting mConnecting;
    protected final Connected mConnected;
    protected final Disconnecting mDisconnecting;
>>>>>>> e94de994c16aa83274259da3c313a88d57b26b48

    protected int mMostRecentState = BluetoothProfile.STATE_DISCONNECTED;

    boolean mRemoteControlConnected = false;
    boolean mBrowsingConnected = false;
    BrowseTree mBrowseTree = null;
    private AvrcpPlayer mAddressedPlayer = new AvrcpPlayer();
    private SparseArray<AvrcpPlayer> mAvailablePlayerList = new SparseArray<AvrcpPlayer>();
    private int mVolumeChangedNotificationsToIgnore = 0;

    GetFolderList mGetFolderList = null;

<<<<<<< HEAD
    private final BluetoothDevice mDevice;
    protected boolean mBrowsingConnected = false;
    protected int prevState = -1;

    AvrcpControllerStateMachine(Context context, BluetoothDevice device) {
        super(TAG);
        mContext = context;
        mDevice = device;
=======
    //Number of items to get in a single fetch
    static final int ITEM_PAGE_SIZE = 20;
    static final int CMD_TIMEOUT_MILLIS = 10000;
    static final int ABS_VOL_TIMEOUT_MILLIS = 1000; //1s
>>>>>>> e94de994c16aa83274259da3c313a88d57b26b48

    AvrcpControllerStateMachine(BluetoothDevice device, AvrcpControllerService service) {
        super(TAG);
        mDevice = device;
        mDeviceAddress = Utils.getByteAddress(mDevice);
        mService = service;
        logD(device.toString());

        mDisconnected = new Disconnected();
        mConnecting = new Connecting();
        mConnected = new Connected();
        mDisconnecting = new Disconnecting();

        addState(mDisconnected);
        addState(mConnecting);
        addState(mConnected);
        addState(mDisconnecting);

        mGetFolderList = new GetFolderList();
        addState(mGetFolderList, mConnected);
<<<<<<< HEAD
        mCoveArtUtils = new CoverArtUtils();
=======
        mAudioManager = (AudioManager) service.getSystemService(Context.AUDIO_SERVICE);
>>>>>>> e94de994c16aa83274259da3c313a88d57b26b48

        setInitialState(mDisconnected);
        mBipStateMachine = AvrcpControllerBipStateMachine.make(this, getHandler(), context);
    }

    BrowseTree.BrowseNode findNode(String parentMediaId) {
        logD("FindNode");
        return mBrowseTree.findBrowseNodeByID(parentMediaId);
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
     * Get the underlying device tracked by this state machine
     *
     * @return device in focus
     */
    public synchronized BluetoothDevice getDevice() {
        return mDevice;
    }

    /**
     * send the connection event asynchronously
     */
    public boolean connect(StackEvent event) {
        sendMessage(CONNECT);
        if (!mBrowsingConnected && event.mBrowsingConnected) {
            onBrowsingConnected();
        }
        mRemoteControlConnected = event.mRemoteControlConnected;
        mBrowsingConnected = event.mBrowsingConnected;
        return true;
    }

    /**
     * send the Disconnect command asynchronously
     */
    public void disconnect() {
        sendMessage(DISCONNECT);
    }

    /**
     * Dump the current State Machine to the string builder.
     *
     * @param sb output string
     */
    public void dump(StringBuilder sb) {
        ProfileService.println(sb, "mDevice: " + mDevice.getAddress() + "("
                + mDevice.getName() + ") " + this.toString());
    }

    @Override
    protected void unhandledMessage(Message msg) {
        Log.w(TAG, "Unhandled message in state " + getCurrentState() + "msg.what=" + msg.what);
    }

    private static void logD(String message) {
        if (DBG) {
            Log.d(TAG, message);
        }
    }

    void onBrowsingConnected() {
        mBrowseTree = new BrowseTree(mDevice);
        mService.sBrowseTree.mRootNode.addChild(mBrowseTree.mRootNode);
        BluetoothMediaBrowserService.notifyChanged(mService
                .sBrowseTree.mRootNode);
        BluetoothMediaBrowserService.notifyChanged(mAddressedPlayer.getPlaybackState());
    }

    void onBrowsingDisconnected() {
        mAddressedPlayer.setPlayStatus(PlaybackState.STATE_ERROR);
        mAddressedPlayer.updateCurrentTrack(null);
        mBrowseTree.mNowPlayingNode.setCached(false);
        BluetoothMediaBrowserService.notifyChanged(mBrowseTree.mNowPlayingNode);
        PlaybackState.Builder pbb = new PlaybackState.Builder();
        pbb.setState(PlaybackState.STATE_ERROR, PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                1.0f).setActions(0);
        BluetoothMediaBrowserService.notifyChanged(pbb.build());
        mService.sBrowseTree.mRootNode.removeChild(
                mBrowseTree.mRootNode);
        BluetoothMediaBrowserService.notifyChanged(mService
                .sBrowseTree.mRootNode);
        BluetoothMediaBrowserService.trackChanged(null);

    }

    private void notifyChanged(BrowseTree.BrowseNode node) {
        BluetoothMediaBrowserService.notifyChanged(node);
    }

    void requestContents(BrowseTree.BrowseNode node) {
        sendMessage(MESSAGE_GET_FOLDER_ITEMS, node);

        logD("Fetching " + node);
    }

    void nowPlayingContentChanged() {
        mBrowseTree.mNowPlayingNode.setCached(false);
        sendMessage(MESSAGE_GET_FOLDER_ITEMS, mBrowseTree.mNowPlayingNode);
    }

    protected class Disconnected extends State {
        @Override
        public void enter() {
<<<<<<< HEAD
            Log.d(TAG, "Enter State: Disconnected mDevice: " + mDevice);
            mBrowsingConnected = false;
            if (prevState != -1) {
                AvrcpControllerService.removeStateMachine(mDevice);
           }
        }

        @Override
        public boolean processMessage(Message msg) {
            if (DBG) Log.d(TAG, " HandleMessage: " + dumpMessageString(msg.what)
                    + ", mDevice: " + mDevice);
            switch (msg.what) {
                case MESSAGE_PROCESS_CONNECTION_CHANGE:
                    if (msg.arg1 == BluetoothProfile.STATE_CONNECTED) {
                        mBrowseTree = new BrowseTree();
                        transitionTo(mConnected);
                        BluetoothDevice rtDevice = (BluetoothDevice) msg.obj;
                        synchronized (mLock) {
                            mRemoteDevice = new RemoteDevice(rtDevice);
                            mAddressedPlayer = new AvrcpPlayer();
                            mIsConnected = true;
                        }
                        MetricsLogger.logProfileConnectionEvent(
                                BluetoothMetricsProto.ProfileId.AVRCP_CONTROLLER);
                        Intent intent = new Intent(
                                BluetoothAvrcpController.ACTION_CONNECTION_STATE_CHANGED);
                        intent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE,
                                BluetoothProfile.STATE_DISCONNECTED);
                        intent.putExtra(BluetoothProfile.EXTRA_STATE,
                                BluetoothProfile.STATE_CONNECTED);
                        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, rtDevice);
                        mContext.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
                    }
                    break;
=======
            logD("Enter Disconnected");
            if (mMostRecentState != BluetoothProfile.STATE_DISCONNECTED) {
                sendMessage(CLEANUP);
            }
            broadcastConnectionStateChanged(BluetoothProfile.STATE_DISCONNECTED);
        }
>>>>>>> e94de994c16aa83274259da3c313a88d57b26b48

        @Override
        public boolean processMessage(Message message) {
            switch (message.what) {
                case CONNECT:
                    logD("Connect");
                    transitionTo(mConnecting);
                    break;
                case CLEANUP:
                    mService.removeStateMachine(AvrcpControllerStateMachine.this);
                    break;
            }
            return true;
        }

        @Override
        public void exit() {
            prevState = BluetoothProfile.STATE_DISCONNECTED;
            log("Exit State: Disconnected: ");
        }
    }

    protected class Connecting extends State {
        @Override
        public void enter() {
            logD("Enter Connecting");
            broadcastConnectionStateChanged(BluetoothProfile.STATE_CONNECTING);
            transitionTo(mConnected);
        }
    }


    class Connected extends State {
        private static final String STATE_TAG = "Avrcp.ConnectedAvrcpController";
        private int mCurrentlyHeldKey = 0;

        @Override
        public void enter() {
            if (mMostRecentState == BluetoothProfile.STATE_CONNECTING) {
                broadcastConnectionStateChanged(BluetoothProfile.STATE_CONNECTED);
                BluetoothMediaBrowserService.addressedPlayerChanged(mSessionCallbacks);
            } else {
                logD("ReEnteringConnected");
            }
            super.enter();
        }

        @Override
        public boolean processMessage(Message msg) {
            logD(STATE_TAG + " processMessage " + msg.what);
            switch (msg.what) {
                case MESSAGE_PROCESS_SET_ABS_VOL_CMD:
                    mVolumeChangedNotificationsToIgnore++;
                    removeMessages(MESSAGE_INTERNAL_ABS_VOL_TIMEOUT);
                    sendMessageDelayed(MESSAGE_INTERNAL_ABS_VOL_TIMEOUT,
                            ABS_VOL_TIMEOUT_MILLIS);
                    setAbsVolume(msg.arg1, msg.arg2);
                    return true;

                case MESSAGE_GET_FOLDER_ITEMS:
                    transitionTo(mGetFolderList);
                    return true;

                case MESSAGE_PLAY_ITEM:
                    //Set Addressed Player
                    playItem((BrowseTree.BrowseNode) msg.obj);
                    return true;

                case MSG_AVRCP_PASSTHRU:
                    passThru(msg.arg1);
                    return true;

<<<<<<< HEAD
                    case MESSAGE_PROCESS_CONNECTION_CHANGE:
                        if (msg.arg1 == BluetoothProfile.STATE_DISCONNECTED) {
                            mCoveArtUtils.msgDisconnectBip(mBipStateMachine,mRemoteDevice);
                            synchronized (mLock) {
                                mIsConnected = false;
                                mRemoteDevice = null;
                            }
                            mBrowseTree.clear();
                            transitionTo(mDisconnected);
                            BluetoothDevice rtDevice = (BluetoothDevice) msg.obj;
                            Intent intent = new Intent(
                                    BluetoothAvrcpController.ACTION_CONNECTION_STATE_CHANGED);
                            intent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE,
                                    BluetoothProfile.STATE_CONNECTED);
                            intent.putExtra(BluetoothProfile.EXTRA_STATE,
                                    BluetoothProfile.STATE_DISCONNECTED);
                            intent.putExtra(BluetoothDevice.EXTRA_DEVICE, rtDevice);
                            mContext.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
                        }
                        break;

                    case MESSAGE_PROCESS_BROWSE_CONNECTION_CHANGE:
                        // Service tells us if the browse is connected or disconnected.
                        // This is useful only for deciding whether to send browse commands rest of
                        // the connection state handling should be done via the message
                        // MESSAGE_PROCESS_CONNECTION_CHANGE.
                        Intent intent = new Intent(
                                AvrcpControllerService.ACTION_BROWSE_CONNECTION_STATE_CHANGED);
                        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, (BluetoothDevice) msg.obj);
                        if (DBG) {
                            Log.d(TAG, "Browse connection state " + msg.arg1);
                        }
                        if (msg.arg1 == 1) {
                            intent.putExtra(BluetoothProfile.EXTRA_STATE,
                                    BluetoothProfile.STATE_CONNECTED);
                            mBrowsingConnected = true;
                        } else if (msg.arg1 == 0) {
                            intent.putExtra(BluetoothProfile.EXTRA_STATE,
                                    BluetoothProfile.STATE_DISCONNECTED);
                            // If browse is disconnected, the next time we connect we should
                            // be at the ROOT.
                            mBrowsingConnected = false;
                        } else {
                            Log.w(TAG, "Incorrect browse state " + msg.arg1);
                        }

                        mContext.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
                        break;

                    case MESSAGE_PROCESS_RC_FEATURES:
                        mRemoteDevice.setRemoteFeatures(msg.arg1);
                        if (msg.arg2 > 0) {
                            mCoveArtUtils.msgProcessRcFeatures
                                (mBipStateMachine, mRemoteDevice,msg.arg2);
                        }
                        break;

                    case MESSAGE_PROCESS_SET_ABS_VOL_CMD:
                        mVolumeChangedNotificationsToIgnore++;
                        removeMessages(MESSAGE_INTERNAL_ABS_VOL_TIMEOUT);
                        sendMessageDelayed(MESSAGE_INTERNAL_ABS_VOL_TIMEOUT,
                                ABS_VOL_TIMEOUT_MILLIS);
                        setAbsVolume(msg.arg1, msg.arg2);
                        break;

                    case MESSAGE_PROCESS_REGISTER_ABS_VOL_NOTIFICATION: {
                        mRemoteDevice.setNotificationLabel(msg.arg1);
                        mRemoteDevice.setAbsVolNotificationRequested(true);
                        int percentageVol = getVolumePercentage();
                        if (DBG) {
                            Log.d(TAG, " Sending Interim Response = " + percentageVol + " label "
                                    + msg.arg1);
                        }
                        AvrcpControllerService.sendRegisterAbsVolRspNative(
                                mRemoteDevice.getBluetoothAddress(), NOTIFICATION_RSP_TYPE_INTERIM,
                                percentageVol, mRemoteDevice.getNotificationLabel());
=======
                case MESSAGE_PROCESS_TRACK_CHANGED:
                    mAddressedPlayer.updateCurrentTrack((MediaMetadata) msg.obj);
                    BluetoothMediaBrowserService.trackChanged((MediaMetadata) msg.obj);
                    return true;

                case MESSAGE_PROCESS_PLAY_STATUS_CHANGED:
                    mAddressedPlayer.setPlayStatus(msg.arg1);
                    BluetoothMediaBrowserService.notifyChanged(mAddressedPlayer.getPlaybackState());
                    if (mAddressedPlayer.getPlaybackState().getState()
                            == PlaybackState.STATE_PLAYING
                            && A2dpSinkService.getFocusState() == AudioManager.AUDIOFOCUS_NONE
                            && !shouldRequestFocus()) {
                        sendMessage(MSG_AVRCP_PASSTHRU,
                                AvrcpControllerService.PASS_THRU_CMD_ID_PAUSE);
>>>>>>> e94de994c16aa83274259da3c313a88d57b26b48
                    }
                    return true;

                case MESSAGE_PROCESS_PLAY_POS_CHANGED:
                    if (msg.arg2 != -1) {
                        mAddressedPlayer.setPlayTime(msg.arg2);

                        BluetoothMediaBrowserService.notifyChanged(
                                mAddressedPlayer.getPlaybackState());
                    }
                    return true;

                case DISCONNECT:
                    transitionTo(mDisconnecting);
                    return true;

<<<<<<< HEAD
                    case MESSAGE_PROCESS_PLAY_POS_CHANGED:
                        if (msg.arg2 != -1) {
                            mAddressedPlayer.setPlayTime(msg.arg2);
                            broadcastPlayBackStateChanged(getCurrentPlayBackState());
                        }
                        break;

                    case MESSAGE_PROCESS_PLAY_STATUS_CHANGED:
                        int status = msg.arg1;
                        mAddressedPlayer.setPlayStatus(status);
                        if (status == PlaybackState.STATE_PLAYING) {
                            a2dpSinkService.informTGStatePlaying(mRemoteDevice.mBTDevice, true);
                        } else if (status == PlaybackState.STATE_PAUSED
                                || status == PlaybackState.STATE_STOPPED) {
                            a2dpSinkService.informTGStatePlaying(mRemoteDevice.mBTDevice, false);
                        }
                        break;

                    case MESSAGE_PROCESS_ADDRESSED_PLAYER_CHANGED:
                        mAddressedPlayerID = msg.arg1;
                        if (DBG) Log.d(TAG, "AddressedPlayer = " + mAddressedPlayerID);
                        AvrcpPlayer updatedPlayer = mAvailablePlayerList.get(mAddressedPlayerID);
                        if (updatedPlayer != null) {
                            mAddressedPlayer = updatedPlayer;
                            if (DBG) Log.d(TAG, "AddressedPlayer = " + mAddressedPlayer.getName());
                        } else {
                            mBrowseTree.mRootNode.setCached(false);
                        }
                        sendMessage(MESSAGE_PROCESS_SET_ADDRESSED_PLAYER);
                        break;

                    case MESSAGE_PROCESS_NOW_PLAYING_CONTENTS_CHANGED:
                        mBrowseTree.mNowPlayingNode.setCached(false);
                        mGetFolderList.setFolder(mBrowseTree.mNowPlayingNode.getID());
                        transitionTo(mGetFolderList);
                        break;

                    case CoverArtUtils.MESSAGE_BIP_CONNECTED:
                    case CoverArtUtils.MESSAGE_BIP_DISCONNECTED:
                    case CoverArtUtils.MESSAGE_BIP_IMAGE_FETCHED:
                    case CoverArtUtils.MESSAGE_BIP_THUMB_NAIL_FETCHED:
                        mCoveArtUtils.processBipAction(mContext, mAddressedPlayer,
                            mRemoteDevice, msg.what, msg);
                        break;

                    default:
                        Log.d(TAG, "Unhandled message" + msg.what);
                        return false;
=======
                default:
                    return super.processMessage(msg);
            }

        }

        private void playItem(BrowseTree.BrowseNode node) {
            if (node == null) {
                Log.w(TAG, "Invalid item to play");
            } else {
                mService.playItemNative(
                        mDeviceAddress, node.getScope(),
                        node.getBluetoothID(), 0);
            }
        }

        private synchronized void passThru(int cmd) {
            logD("msgPassThru " + cmd);
            // Some keys should be held until the next event.
            if (mCurrentlyHeldKey != 0) {
                mService.sendPassThroughCommandNative(
                        mDeviceAddress, mCurrentlyHeldKey,
                        AvrcpControllerService.KEY_STATE_RELEASED);

                if (mCurrentlyHeldKey == cmd) {
                    // Return to prevent starting FF/FR operation again
                    mCurrentlyHeldKey = 0;
                    return;
                } else {
                    // FF/FR is in progress and other operation is desired
                    // so after stopping FF/FR, not returning so that command
                    // can be sent for the desired operation.
                    mCurrentlyHeldKey = 0;
>>>>>>> e94de994c16aa83274259da3c313a88d57b26b48
                }
            }

            // Send the pass through.
            mService.sendPassThroughCommandNative(mDeviceAddress, cmd,
                    AvrcpControllerService.KEY_STATE_PRESSED);

            if (isHoldableKey(cmd)) {
                // Release cmd next time a command is sent.
                mCurrentlyHeldKey = cmd;
            } else {
                mService.sendPassThroughCommandNative(mDeviceAddress,
                        cmd, AvrcpControllerService.KEY_STATE_RELEASED);
            }
        }

<<<<<<< HEAD
        @Override
        public void exit() {
            prevState = BluetoothProfile.STATE_CONNECTED;
            log("Exit State: Connected: " + mDevice + ", currentMsg: " + getCurrentMessage().what);
        }
=======
        private boolean isHoldableKey(int cmd) {
            return (cmd == AvrcpControllerService.PASS_THRU_CMD_ID_REWIND)
                    || (cmd == AvrcpControllerService.PASS_THRU_CMD_ID_FF);
        }


>>>>>>> e94de994c16aa83274259da3c313a88d57b26b48
    }


    // Handle the get folder listing action
    // a) Fetch the listing of folders
    // b) Once completed return the object listing
    class GetFolderList extends State {
        private static final String STATE_TAG = "Avrcp.GetFolderList";

        boolean mAbort;
        BrowseTree.BrowseNode mBrowseNode;
        BrowseTree.BrowseNode mNextStep;

        @Override
        public void enter() {
            logD(STATE_TAG + " Entering GetFolderList");
            // Setup the timeouts.
            sendMessageDelayed(MESSAGE_INTERNAL_CMD_TIMEOUT, CMD_TIMEOUT_MILLIS);
            super.enter();
            mAbort = false;
            Message msg = getCurrentMessage();
            if (msg.what == MESSAGE_GET_FOLDER_ITEMS) {
                {
                    logD(STATE_TAG + " new Get Request");
                    mBrowseNode = (BrowseTree.BrowseNode) msg.obj;
                }
            }

            if (mBrowseNode == null) {
                transitionTo(mConnected);
            } else {
                navigateToFolderOrRetrieve(mBrowseNode);
            }
        }

        @Override
        public boolean processMessage(Message msg) {
            logD(STATE_TAG + " processMessage " + msg.what);
            switch (msg.what) {
                case MESSAGE_PROCESS_GET_FOLDER_ITEMS:
                    ArrayList<MediaItem> folderList = (ArrayList<MediaItem>) msg.obj;
                    int endIndicator = mBrowseNode.getExpectedChildren() - 1;
                    logD("GetFolderItems: End " + endIndicator
                            + " received " + folderList.size());

                    // Always update the node so that the user does not wait forever
                    // for the list to populate.
                    mBrowseNode.addChildren(folderList);
                    notifyChanged(mBrowseNode);

                    if (mBrowseNode.getChildrenCount() >= endIndicator || folderList.size() == 0
                            || mAbort) {
                        // If we have fetched all the elements or if the remotes sends us 0 elements
                        // (which can lead us into a loop since mCurrInd does not proceed) we simply
                        // abort.
                        mBrowseNode.setCached(true);
                        transitionTo(mConnected);
                    } else {
                        // Fetch the next set of items.
                        fetchContents(mBrowseNode);
                        // Reset the timeout message since we are doing a new fetch now.
                        removeMessages(MESSAGE_INTERNAL_CMD_TIMEOUT);
                        sendMessageDelayed(MESSAGE_INTERNAL_CMD_TIMEOUT, CMD_TIMEOUT_MILLIS);
                    }
                    break;
                case MESSAGE_PROCESS_SET_BROWSED_PLAYER:
                    mBrowseTree.setCurrentBrowsedPlayer(mNextStep.getID(), msg.arg1, msg.arg2);
                    removeMessages(MESSAGE_INTERNAL_CMD_TIMEOUT);
                    sendMessageDelayed(MESSAGE_INTERNAL_CMD_TIMEOUT, CMD_TIMEOUT_MILLIS);
                    navigateToFolderOrRetrieve(mBrowseNode);
                    break;

                case MESSAGE_PROCESS_FOLDER_PATH:
                    mBrowseTree.setCurrentBrowsedFolder(mNextStep.getID());
                    mBrowseTree.getCurrentBrowsedFolder().setExpectedChildren(msg.arg1);

                    if (mAbort) {
                        transitionTo(mConnected);
                    } else {
                        removeMessages(MESSAGE_INTERNAL_CMD_TIMEOUT);
                        sendMessageDelayed(MESSAGE_INTERNAL_CMD_TIMEOUT, CMD_TIMEOUT_MILLIS);
                        navigateToFolderOrRetrieve(mBrowseNode);
                    }
                    break;

                case MESSAGE_PROCESS_GET_PLAYER_ITEMS:
                    BrowseTree.BrowseNode rootNode = mBrowseTree.mRootNode;
                    if (!rootNode.isCached()) {
                        List<AvrcpPlayer> playerList = (List<AvrcpPlayer>) msg.obj;
                        mAvailablePlayerList.clear();
                        for (AvrcpPlayer player : playerList) {
                            mAvailablePlayerList.put(player.getId(), player);
                        }
                        rootNode.addChildren(playerList);
                        mBrowseTree.setCurrentBrowsedFolder(BrowseTree.ROOT);
                        rootNode.setExpectedChildren(playerList.size());
                        rootNode.setCached(true);
                        notifyChanged(rootNode);
                    }
                    transitionTo(mConnected);
                    break;

                case MESSAGE_INTERNAL_CMD_TIMEOUT:
                    // We have timed out to execute the request, we should simply send
                    // whatever listing we have gotten until now.
                    Log.w(TAG, "TIMEOUT");
                    transitionTo(mConnected);
                    break;

                case MESSAGE_PROCESS_GET_FOLDER_ITEMS_OUT_OF_RANGE:
                    // If we have gotten an error for OUT OF RANGE we have
                    // already sent all the items to the client hence simply
                    // transition to Connected state here.
                    mBrowseNode.setCached(true);
                    transitionTo(mConnected);
                    break;

                case MESSAGE_GET_FOLDER_ITEMS:
                    if (!mBrowseNode.equals(msg.obj)) {
                        if (mBrowseNode.getScope()
                                == ((BrowseTree.BrowseNode) msg.obj).getScope()) {
                            mAbort = true;
                        }
                        deferMessage(msg);
                        logD("GetFolderItems: Go Get Another Directory");
                    } else {
                        logD("GetFolderItems: Get The Same Directory, ignore");
                    }
                    break;

                case CONNECT:
                case DISCONNECT:
                case MSG_AVRCP_PASSTHRU:
                case MESSAGE_PROCESS_SET_ABS_VOL_CMD:
                case MESSAGE_PROCESS_REGISTER_ABS_VOL_NOTIFICATION:
                case MESSAGE_PROCESS_TRACK_CHANGED:
                case MESSAGE_PROCESS_PLAY_POS_CHANGED:
                case MESSAGE_PROCESS_PLAY_STATUS_CHANGED:
                case MESSAGE_PROCESS_VOLUME_CHANGED_NOTIFICATION:
                    // All of these messages should be handled by parent state immediately.
                    return false;

                default:
                    logD(STATE_TAG + " deferring message " + msg.what
                                + " to connected!");
                    deferMessage(msg);
            }
            return true;
        }

        private void fetchContents(BrowseTree.BrowseNode target) {
            int start = target.getChildrenCount();
            int end = Math.min(target.getExpectedChildren(), target.getChildrenCount()
                    + ITEM_PAGE_SIZE) - 1;
            switch (target.getScope()) {
                case AvrcpControllerService.BROWSE_SCOPE_PLAYER_LIST:
                    mService.getPlayerListNative(mDeviceAddress,
                            start, end);
                    break;
                case AvrcpControllerService.BROWSE_SCOPE_NOW_PLAYING:
                    mService.getNowPlayingListNative(
                            mDeviceAddress, start, end);
                    break;
                case AvrcpControllerService.BROWSE_SCOPE_VFS:
                    mService.getFolderListNative(mDeviceAddress,
                            start, end);
                    break;
                default:
                    Log.e(TAG, STATE_TAG + " Scope " + target.getScope()
                            + " cannot be handled here.");
            }
        }

        /* One of several things can happen when trying to get a folder list
         *
         *
         * 0: The folder handle is no longer valid
         * 1: The folder contents can be retrieved directly (NowPlaying, Root, Current)
         * 2: The folder is a browsable player
         * 3: The folder is a non browsable player
         * 4: The folder is not a child of the current folder
         * 5: The folder is a child of the current folder
         *
         */
        private void navigateToFolderOrRetrieve(BrowseTree.BrowseNode target) {
            mNextStep = mBrowseTree.getNextStepToFolder(target);
            logD("NAVIGATING From "
                    + mBrowseTree.getCurrentBrowsedFolder().toString());
            logD("NAVIGATING Toward " + target.toString());
            if (mNextStep == null) {
                return;
            } else if (target.equals(mBrowseTree.mNowPlayingNode)
                    || target.equals(mBrowseTree.mRootNode)
                    || mNextStep.equals(mBrowseTree.getCurrentBrowsedFolder())) {
                fetchContents(mNextStep);
            } else if (mNextStep.isPlayer()) {
                logD("NAVIGATING Player " + mNextStep.toString());
                if (mNextStep.isBrowsable()) {
                    mService.setBrowsedPlayerNative(
                            mDeviceAddress, (int) mNextStep.getBluetoothID());
                } else {
                    logD("Player doesn't support browsing");
                    mNextStep.setCached(true);
                    transitionTo(mConnected);
                }
            } else if (mNextStep.equals(mBrowseTree.mNavigateUpNode)) {
                logD("NAVIGATING UP " + mNextStep.toString());
                mNextStep = mBrowseTree.getCurrentBrowsedFolder().getParent();
                mBrowseTree.getCurrentBrowsedFolder().setCached(false);

                mService.changeFolderPathNative(
                        mDeviceAddress,
                        AvrcpControllerService.FOLDER_NAVIGATION_DIRECTION_UP,
                        0);

            } else {
                logD("NAVIGATING DOWN " + mNextStep.toString());
                mService.changeFolderPathNative(
                        mDeviceAddress,
                        AvrcpControllerService.FOLDER_NAVIGATION_DIRECTION_DOWN,
                        mNextStep.getBluetoothID());
            }
        }

        @Override
        public void exit() {
            removeMessages(MESSAGE_INTERNAL_CMD_TIMEOUT);
            mBrowseNode = null;
            super.exit();
        }
    }

    protected class Disconnecting extends State {
        @Override
        public void enter() {
            onBrowsingDisconnected();
            broadcastConnectionStateChanged(BluetoothProfile.STATE_DISCONNECTING);
            transitionTo(mDisconnected);
        }
    }


    private void setAbsVolume(int absVol, int label) {
        int maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int currIndex = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int newIndex = (maxVolume * absVol) / ABS_VOL_BASE;
        logD(" setAbsVolume =" + absVol + " maxVol = " + maxVolume
                + " cur = " + currIndex + " new = " + newIndex);
        /*
         * In some cases change in percentage is not sufficient enough to warrant
         * change in index values which are in range of 0-15. For such cases
         * no action is required
         */
        if (newIndex != currIndex) {
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newIndex,
                    AudioManager.FLAG_SHOW_UI);
        }
        mService.sendAbsVolRspNative(mDeviceAddress, absVol, label);
    }

    MediaSession.Callback mSessionCallbacks = new MediaSession.Callback() {
        @Override
        public void onPlay() {
            logD("onPlay");
            onPrepare();
            sendMessage(MSG_AVRCP_PASSTHRU, AvrcpControllerService.PASS_THRU_CMD_ID_PLAY);
        }

        @Override
        public void onPause() {
            logD("onPause");
            sendMessage(MSG_AVRCP_PASSTHRU, AvrcpControllerService.PASS_THRU_CMD_ID_PAUSE);
        }

        @Override
        public void onSkipToNext() {
            logD("onSkipToNext");
            sendMessage(MSG_AVRCP_PASSTHRU, AvrcpControllerService.PASS_THRU_CMD_ID_FORWARD);
        }

        @Override
        public void onSkipToPrevious() {
            logD("onSkipToPrevious");
            sendMessage(MSG_AVRCP_PASSTHRU, AvrcpControllerService.PASS_THRU_CMD_ID_BACKWARD);
        }
<<<<<<< HEAD
        mCoveArtUtils.closeBip(mBipStateMachine);
        // we should disacrd, all currently queuedup messages.
        quitNow();
    }

    void dump(StringBuilder sb) {
        if (mRemoteDevice == null) return;
        BluetoothDevice device = mRemoteDevice.mBTDevice;
        if (device == null) return;
        ProfileService.println(sb, "mCurrentDevice: " + device.getAddress() + "("
                + device.getName() + ") " + this.toString());
    }
=======
>>>>>>> e94de994c16aa83274259da3c313a88d57b26b48

        @Override
        public void onSkipToQueueItem(long id) {
            logD("onSkipToQueueItem" + id);
            BrowseTree.BrowseNode node = mBrowseTree.getTrackFromNowPlayingList((int) id);
            if (node != null) {
                sendMessage(MESSAGE_PLAY_ITEM, node);
            }
        }

        @Override
        public void onStop() {
            logD("onStop");
            sendMessage(MSG_AVRCP_PASSTHRU, AvrcpControllerService.PASS_THRU_CMD_ID_STOP);
        }

        @Override
        public void onPrepare() {
            logD("onPrepare");
            A2dpSinkService a2dpSinkService = A2dpSinkService.getA2dpSinkService();
            if (a2dpSinkService != null) {
                a2dpSinkService.requestAudioFocus(mDevice, true);
            }
        }

        @Override
        public void onRewind() {
            logD("onRewind");
            sendMessage(MSG_AVRCP_PASSTHRU, AvrcpControllerService.PASS_THRU_CMD_ID_REWIND);
        }

        @Override
        public void onFastForward() {
            logD("onFastForward");
            sendMessage(MSG_AVRCP_PASSTHRU, AvrcpControllerService.PASS_THRU_CMD_ID_FF);
        }

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            // Play the item if possible.
            onPrepare();
            BrowseTree.BrowseNode node = mBrowseTree.findBrowseNodeByID(mediaId);
            Log.w(TAG, "Play Node not found");
            sendMessage(MESSAGE_PLAY_ITEM, node);
        }
    };

    protected void broadcastConnectionStateChanged(int currentState) {
        if (mMostRecentState == currentState) {
            return;
        }
<<<<<<< HEAD
        mContext.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
    }

    private void setAbsVolume(int absVol, int label) {
        int maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int currIndex = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        /* If SetAbsVolume Control Cmd is received from non-Streaming device then the
         * requested volume level will not be set fot rendering and current Abs vol level
         *  at DUT (sink: rendering device) will be sent in response. */
        Log.d(TAG, "Streaming device: " + A2dpSinkService.getCurrentStreamingDevice()
                + " Device:" + mDevice);
        if (!mDevice.equals(A2dpSinkService.getCurrentStreamingDevice())) {
            absVol = (currIndex * ABS_VOL_BASE) / maxVolume;
            Log.w(TAG, "Volume change request came from non-streaming device," +
                    "respond with current absVol: " + absVol);
            AvrcpControllerService.sendAbsVolRspNative(mRemoteDevice.getBluetoothAddress(), absVol,
                label);
            return;
        }
        // Ignore first volume command since phone may not know difference between stream volume
        // and amplifier volume.
        if (mRemoteDevice.getFirstAbsVolCmdRecvd()) {
            int newIndex = (maxVolume * absVol) / ABS_VOL_BASE;
            if (DBG) {
                Log.d(TAG, " setAbsVolume =" + absVol + " maxVol = " + maxVolume
                        + " cur = " + currIndex + " new = " + newIndex);
            }
            /*
             * In some cases change in percentage is not sufficient enough to warrant
             * change in index values which are in range of 0-15. For such cases
             * no action is required
             */
            if (newIndex != currIndex) {
                mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newIndex,
                        AudioManager.FLAG_SHOW_UI);
            }
        } else {
            mRemoteDevice.setFirstAbsVolCmdRecvd();
            absVol = (currIndex * ABS_VOL_BASE) / maxVolume;
            if (DBG) Log.d(TAG, " SetAbsVol recvd for first time, respond with " + absVol);
=======
        if (currentState == BluetoothProfile.STATE_CONNECTED) {
            MetricsLogger.logProfileConnectionEvent(
                    BluetoothMetricsProto.ProfileId.AVRCP_CONTROLLER);
>>>>>>> e94de994c16aa83274259da3c313a88d57b26b48
        }
        logD("Connection state " + mDevice + ": " + mMostRecentState + "->" + currentState);
        Intent intent = new Intent(BluetoothAvrcpController.ACTION_CONNECTION_STATE_CHANGED);
        intent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, mMostRecentState);
        intent.putExtra(BluetoothProfile.EXTRA_STATE, currentState);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mDevice);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        mMostRecentState = currentState;
        mService.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
    }

<<<<<<< HEAD
    private int getVolumePercentage() {
        int maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int currIndex = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int percentageVol = ((currIndex * ABS_VOL_BASE) / maxVolume);
        return percentageVol;
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(AudioManager.VOLUME_CHANGED_ACTION)) {
                int streamType = intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, -1);
                if (streamType == AudioManager.STREAM_MUSIC) {
                    sendMessage(MESSAGE_PROCESS_VOLUME_CHANGED_NOTIFICATION);
                }
            }
        }
    };

    public static String dumpMessageString(int message) {
        String str = "UNKNOWN";
        switch (message) {
            case MESSAGE_SEND_PASS_THROUGH_CMD:
                str = "REQ_PASS_THROUGH_CMD";
                break;
            case MESSAGE_SEND_GROUP_NAVIGATION_CMD:
                str = "REQ_GRP_NAV_CMD";
                break;
            case MESSAGE_PROCESS_SET_ABS_VOL_CMD:
                str = "CB_SET_ABS_VOL_CMD";
                break;
            case MESSAGE_PROCESS_REGISTER_ABS_VOL_NOTIFICATION:
                str = "CB_REGISTER_ABS_VOL";
                break;
            case MESSAGE_PROCESS_TRACK_CHANGED:
                str = "CB_TRACK_CHANGED";
                break;
            case MESSAGE_PROCESS_PLAY_POS_CHANGED:
                str = "CB_PLAY_POS_CHANGED";
                break;
            case MESSAGE_PROCESS_PLAY_STATUS_CHANGED:
                str = "CB_PLAY_STATUS_CHANGED";
                break;
            case MESSAGE_PROCESS_RC_FEATURES:
                str = "CB_RC_FEATURES";
                break;
            case MESSAGE_PROCESS_CONNECTION_CHANGE:
                str = "CB_CONN_CHANGED";
                break;
            case CoverArtUtils.MESSAGE_BIP_CONNECTED:
            case CoverArtUtils.MESSAGE_BIP_DISCONNECTED:
            case CoverArtUtils.MESSAGE_BIP_IMAGE_FETCHED:
            case CoverArtUtils.MESSAGE_BIP_THUMB_NAIL_FETCHED:
                str = mCoveArtUtils.dumpMessageString(message);
                break;
            default:
                str = Integer.toString(message);
                break;
        }
        return str;
=======
    private boolean shouldRequestFocus() {
        return mService.getResources()
                .getBoolean(R.bool.a2dp_sink_automatically_request_audio_focus);
>>>>>>> e94de994c16aa83274259da3c313a88d57b26b48
    }

    BluetoothDevice getDevice() {
        return mDevice;
    }

    boolean isBrowsingConnected() {
        Log.d(TAG, "mBrowsingConnected = " + mBrowsingConnected);
        return mBrowsingConnected;
    }
}
