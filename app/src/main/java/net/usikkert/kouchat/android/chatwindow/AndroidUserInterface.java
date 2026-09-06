
/***************************************************************************
 *   Copyright 2006-2019 by Christian Ihle                                 *
 *   contact@kouchat.net                                                   *
 *                                                                         *
 *   This file is part of KouChat.                                         *
 *                                                                         *
 *   KouChat is free software; you can redistribute it and/or modify       *
 *   it under the terms of the GNU Lesser General Public License as        *
 *   published by the Free Software Foundation, either version 3 of        *
 *   the License, or (at your option) any later version.                   *
 *                                                                         *
 *   KouChat is distributed in the hope that it will be useful,            *
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU      *
 *   Lesser General Public License for more details.                       *
 *                                                                         *
 *   You should have received a copy of the GNU Lesser General Public      *
 *   License along with KouChat.                                           *
 *   If not, see <http://www.gnu.org/licenses/>.                           *
 ***************************************************************************/

package net.usikkert.kouchat.android.chatwindow;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.concurrent.ExecutionException;

import net.usikkert.kouchat.Constants;
import net.usikkert.kouchat.android.R;
import net.usikkert.kouchat.android.component.Command;
import net.usikkert.kouchat.android.component.CommandWithToastOnExceptionAsyncTask;
import net.usikkert.kouchat.android.controller.MainChatController;
import net.usikkert.kouchat.android.controller.ReceiveFileController;
import net.usikkert.kouchat.android.controller.TrustKeyController;
import net.usikkert.kouchat.android.filetransfer.AndroidFileTransferListener;
import net.usikkert.kouchat.android.filetransfer.AndroidFileUtils;
import net.usikkert.kouchat.android.notification.NotificationService;
import net.usikkert.kouchat.android.settings.AndroidSettings;
import net.usikkert.kouchat.android.settings.AndroidSettingsSaver;
import net.usikkert.kouchat.crypto.CryptoStatusListener;
import net.usikkert.kouchat.crypto.TrustCallback;
import net.usikkert.kouchat.crypto.TrustPrompt;
import net.usikkert.kouchat.event.NetworkConnectionListener;
import net.usikkert.kouchat.message.CoreMessages;
import net.usikkert.kouchat.misc.ChatLogger;
import net.usikkert.kouchat.misc.CommandException;
import net.usikkert.kouchat.misc.CommandParser;
import net.usikkert.kouchat.misc.Controller;
import net.usikkert.kouchat.misc.ErrorHandler;
import net.usikkert.kouchat.misc.MessageController;
import net.usikkert.kouchat.misc.Topic;
import net.usikkert.kouchat.misc.User;
import net.usikkert.kouchat.misc.UserList;
import net.usikkert.kouchat.net.FileReceiver;
import net.usikkert.kouchat.net.FileSender;
import net.usikkert.kouchat.net.FileToSend;
import net.usikkert.kouchat.net.FileTransfer;
import net.usikkert.kouchat.net.TransferList;
import net.usikkert.kouchat.settings.NetworkMode;
import net.usikkert.kouchat.ui.ChatWindow;
import net.usikkert.kouchat.ui.UserInterface;
import net.usikkert.kouchat.util.Sleeper;
import net.usikkert.kouchat.util.Tools;
import net.usikkert.kouchat.util.Validate;

import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.widget.Toast;

/**
 * Implementation of a KouChat user interface that communicates with the Android GUI.
 *
 * @author Christian Ihle
 */
public class AndroidUserInterface implements UserInterface, ChatWindow, TrustPrompt {

    private final MessageController msgController;
    private final Controller controller;
    private final UserList userList;
    private final User me;
    private final MessageStylerWithHistory messageStyler;
    private final Context context;
    private final AndroidSettings settings;
    private final NotificationService notificationService;
    private final CommandParser commandParser;
    private final TransferList transferList;
    private final AndroidFileUtils androidFileUtils;
    private final Sleeper sleeper;
    private final ErrorHandler errorHandler;

    /** Pending trust dialogs, keyed by peer user code, so the activity can deliver the decision. */
    private final java.util.Map<Integer, TrustCallback> pendingTrustCallbacks = new java.util.concurrent.ConcurrentHashMap<>();

    private MainChatController mainChatController;

    public AndroidUserInterface(final Context context, final AndroidSettings settings,
                                final NotificationService notificationService) {
        Validate.notNull(context, "Context can not be null");
        Validate.notNull(settings, "Settings can not be null");
        Validate.notNull(notificationService, "NotificationService can not be null");

        this.context = context;
        this.settings = settings;
        this.notificationService = notificationService;
        this.errorHandler = new ErrorHandler(); // TODO add a listener for showing errors

        messageStyler = new MessageStylerWithHistory(context);
        msgController = new MessageController(this, this, settings, errorHandler);
        final CoreMessages coreMessages = new CoreMessages();
        controller = new Controller(this, settings, new AndroidSettingsSaver(), coreMessages, errorHandler);
        controller.setTrustPrompt(this);
        controller.getCryptoManager().setStatusListener(new CryptoStatusListener() {
            @Override
            public void peerUnsupported(final User peer) {
                msgController.showSystemMessage(coreMessages.getMessage(
                        "core.crypto.systemMessage.peerUnsupported", peer.getNick()));
            }
        });
        commandParser = new CommandParser(controller, this, settings, coreMessages);
        androidFileUtils = new AndroidFileUtils();
        sleeper = new Sleeper();

        userList = controller.getUserList();
        transferList = controller.getTransferList();
        me = settings.getMe();
    }

    /**
     * Just accepts the file transfer for now. A file receiver will be added, and
     * {@link #showFileSave(FileReceiver)} will be called afterwards.
     */
    @Override
    public boolean askFileSave(final String user, final String fileName, final String size) {
        return true;
    }

    /**
     * Uses a notification to ask the user to accept or reject this file transfer request.
     * The notification is canceled afterwards.
     *
     * <p>Expects this to be called from a different thread, as it waits for an answer
     * before it continues execution.</p>
     *
     * @param fileReceiver Information about the file to save.
     */
    @Override
    public void showFileSave(final FileReceiver fileReceiver) {
        notificationService.notifyNewFileTransfer(fileReceiver);
        openReceiveFileDialog(fileReceiver);

        while (!fileReceiver.isAccepted() && !fileReceiver.isRejected() && !fileReceiver.isCanceled()) {
            sleeper.sleep(500);
        }

        if (!fileReceiver.isAccepted()) {
            notificationService.cancelFileTransferNotification(fileReceiver);
        }
    }

    /**
     * Opens the accept/reject dialog for an incoming file transfer as a popup
     * window, so the user does not have to open the notification shade first.
     *
     * @param fileReceiver The incoming file transfer.
     */
    private void openReceiveFileDialog(final FileReceiver fileReceiver) {
        final Intent intent = new Intent(context, ReceiveFileController.class);
        intent.putExtra("userCode", fileReceiver.getUser().getCode());
        intent.putExtra("fileTransferId", fileReceiver.getId());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            context.startActivity(intent);
        }

        catch (final Exception e) {
            // Starting the activity from the background can be restricted on some Android versions.
            // The notification is still shown, so the user can open the dialog from there.
        }
    }

    /**
     * Registers a listener for receiving files, and makes sure the file is stored in the downloads directory
     * with a unique name.
     *
     * @param fileRes The file reception object.
     */
    @Override
    public void showTransfer(final FileReceiver fileRes) {
        Validate.notNull(fileRes, "FileReceiver can not be null");

        final File fileInDownloads = androidFileUtils.createFileInDownloadsWithAvailableName(context, fileRes.getFileName());
        fileRes.setFile(fileInDownloads);

        new AndroidFileTransferListener(fileRes, context, androidFileUtils,
                                        msgController, notificationService);
    }

    /**
     * Registers a listener for sending files.
     *
     * @param fileSend The file sending object.
     */
    @Override
    public void showTransfer(final FileSender fileSend) {
        Validate.notNull(fileSend, "FileSender can not be null");

        new AndroidFileTransferListener(fileSend, context, androidFileUtils,
                                        msgController, notificationService);
    }

    /**
     * Sends an expose message to a specific ip address, to connect to a client
     * that may not be on the same multicast network.
     *
     * @param ipAddress The ip address to connect to.
     */
    public void connectToIp(final String ipAddress) {
        controller.connectToIp(ipAddress);
    }

    /**
     * Switches the network mode between broadcast and encrypted P2P mesh.
     *
     * <p>Dispatched to a background thread: P2P mode triggers handshakes (TCP writes)
     * which must not run on Android's main thread.</p>
     *
     * @param mode The new network mode.
     */
    public void setNetworkMode(final NetworkMode mode) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                controller.setNetworkMode(mode);

                // Refresh the title bar and the network status bar with the new mode.
                showTopic();
            }
        }, "SetNetworkMode").start();
    }

    /**
     * Actively initiates the encrypted handshake with a peer, e.g. when the user opens
     * a private chat with them. Shows "Establishing encrypted connection..." in the
     * private chat, then the trust dialog when the peer's key arrives.
     *
     * <p>Dispatched to a background thread: the handshake sends network messages which
     * must not run on Android's main thread.</p>
     *
     * @param user The peer to establish an encrypted channel with.
     */
    public void initiateEncryptedChat(final User user) {
        if (user == null || user.isMe()) {
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                controller.initiatePrivateChat(user);
            }
        }, "InitiateEncryptedChat").start();
    }

    // ----- End-to-end encryption: trust prompt -----

    /**
     * Called (from the crypto responder thread) when a peer's key needs a trust decision.
     * Posts a notification and launches the {@link TrustKeyController} activity; the decision
     * is delivered back via {@link #reportTrustDecision(int, boolean)}.
     *
     * <p>The notification is posted first: when the app is backgrounded, Android blocks the
     * direct activity start (silently, without an exception), but the notification still
     * shows and tapping it opens the trust dialog.</p>
     *
     * {@inheritDoc}
     */
    @Override
    public void requestTrust(final User peer, final String fingerprint, final TrustCallback callback) {
        pendingTrustCallbacks.put(peer.getCode(), callback);

        notificationService.notifyTrustRequested(peer, fingerprint);

        final Intent intent = new Intent(context, TrustKeyController.class);
        intent.putExtra("userCode", peer.getCode());
        intent.putExtra("userNick", peer.getNick());
        intent.putExtra("fingerprint", fingerprint);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            context.startActivity(intent);
        }

        catch (final Exception e) {
            // Starting the activity can be restricted when backgrounded. The notification
            // remains, so the user can still open the dialog from there - don't decline.
        }
    }

    /**
     * Dismisses the trust notification for a peer, e.g. when the trust dialog is showing.
     *
     * @param userCode The user code of the peer.
     */
    public void dismissTrustNotification(final int userCode) {
        notificationService.cancelTrustNotification(userCode);
    }

    /**
     * Called by {@link TrustKeyController} when the user has decided whether to trust a peer.
     *
     * <p>Dispatched to a background thread because the crypto manager's response does network
     * I/O (sending KEYTRUST/KEYTRUSTACK over TCP) and file I/O (persisting trust) - both
     * forbidden on Android's main thread ({@code NetworkOnMainThreadException}).</p>
     *
     * @param userCode The peer's user code.
     * @param trusted True if the user chose to trust the key.
     */
    public void reportTrustDecision(final int userCode, final boolean trusted) {
        final TrustCallback callback = pendingTrustCallbacks.remove(userCode);
        notificationService.cancelTrustNotification(userCode);

        if (callback != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        callback.onDecision(trusted);
                    }

                    catch (final RuntimeException e) {
                        android.util.Log.e("KouChat", "Trust decision handling failed", e);
                    }
                }
            }, "TrustDecision").start();
        }
    }

    /**
     * Gets a file receiver object for the given user with the given file transfer id.
     *
     * @param userCode The unique code of the user who requests to send a file.
     * @param fileTransferId The id of the request to send a file.
     * @return The file transfer object, if found, or <code>null</code> if not found.
     */
    public FileReceiver getFileReceiver(final int userCode, final int fileTransferId) {
        final User user = getUser(userCode);
        return transferList.getFileReceiver(user, fileTransferId);
    }

    public void cancelFileTransfer(final int userCode, final int fileTransferId) {
        final User user = getUser(userCode);
        final FileTransfer fileTransfer = transferList.getFileTransfer(user, fileTransferId);

        if (fileTransfer != null) {
            commandParser.cancelFileTransfer(fileTransfer);
        }
    }

    /**
     * Updates the title and topic of the main chat.
     *
     * <p>If there is no topic, then <code>null</code> is sent as the topic, to hide line number two.</p>
     * <p>If the user is away, then (Away) is included in the title.</p>
     * <p>If the network connection is down, connection details are shown instead.</p>
     *
     * <p>Looks like this:</p>
     * <pre>
     *   <code>Nick name (Away) - KouChat</code>
     *   <code>The topic - Nick name of the user that set the topic</code>
     * </pre>
     *
     * <p>Example:</p>
     * <pre>
     *   <code>Penny (Away) - KouChat</code>
     *   <code>Knock knock - Sheldon</code>
     * </pre>
     *
     * <p>Or just:</p>
     * <pre>
     *   <code>Penny - KouChat</code>
     * </pre>
     *
     * <p>If never connected:</p>
     * <pre>
     *   <code>Not connected - KouChat</code>
     * </pre>
     *
     * <p>If the connection has been lost, after having been connected earlier:</p>
     * <pre>
     *   <code>Connection lost - KouChat</code>
     * </pre>
     */
    @Override
    public void showTopic() {
        if (mainChatController != null) {
            final String title = formatTitle();
            final String topic = formatTopic();

            mainChatController.updateTitleAndSubtitle(title, topic);
            mainChatController.updateNetworkStatus(formatNetworkStatus());
        }
    }

    /**
     * Builds the text for the network status bar: the current network mode and
     * the state of the network connection.
     *
     * @return The status text, like "Network: Multicast - Connected".
     */
    private String formatNetworkStatus() {
        final NetworkMode networkMode = controller.getNetworkMode() == null
                ? NetworkMode.MULTICAST : controller.getNetworkMode();

        final int modeResourceId;

        switch (networkMode) {
            case P2P:
                modeResourceId = R.string.main_chat_status_mode_p2p;
                break;

            case UNICAST:
                modeResourceId = R.string.main_chat_status_mode_unicast;
                break;

            case BROADCAST:
                modeResourceId = R.string.main_chat_status_mode_broadcast;
                break;

            default:
                modeResourceId = R.string.main_chat_status_mode_multicast;
                break;
        }

        final String modeText = context.getString(R.string.main_chat_status_network_mode,
                context.getString(modeResourceId));

        final String connectionText;

        if (controller.isConnected()) {
            connectionText = context.getString(R.string.main_chat_status_connected);
        }

        else if (controller.isLoggedOn()) {
            connectionText = context.getString(R.string.main_chat_status_connection_lost);
        }

        else {
            connectionText = context.getString(R.string.main_chat_status_not_connected);
        }

        return modeText + " - " + connectionText;
    }

    private String formatTitle() {
        if (!controller.isConnected()) {
            if (controller.isLoggedOn()) {
                return me.getNick() + " - Connection lost - " + Constants.APP_NAME;
            }

            else {
                return me.getNick() + " - Not connected - " + Constants.APP_NAME;
            }
        }

        else if (isAway()) {
            return me.getNick() + " (Away) - " + Constants.APP_NAME;
        }

        return me.getNick() + " - " + Constants.APP_NAME;
    }

    private String formatTopic() {
        if (!controller.isConnected()) {
            return null;
        }

        final Topic topic = controller.getTopic();

        if (topic.hasTopic()) {
            return topic.getTopic() + " - " + topic.getNick();
        } else {
            return null;
        }
    }

    /**
     * Gets the current topic.
     *
     * @return The current topic.
     */
    public String getTopic() {
        return controller.getTopic().getTopic();
    }

    /**
     * Changes the topic to the specified topic. Set to empty to remove the topic.
     *
     * @param topic The new topic to set.
     */
    public void changeTopic(final String topic) {
        new CommandWithToastOnExceptionAsyncTask(context, new Command() {
            @Override
            public void runCommand() throws CommandException {
                commandParser.fixTopic(topic);
            }
        }).execute();
    }

    @Override
    public void clearChat() {

    }

    /**
     * Updates the title and topic when away mode is changed.
     *
     * @param away If the user is away.
     */
    @Override
    public void changeAway(final boolean away) {
        showTopic();
    }

    /**
     * Checks if the application user is currently away.
     *
     * @return If away.
     */
    public boolean isAway() {
        return me.isAway();
    }

    /**
     * Sets the application user as away, with the specified away message.
     *
     * @param awayMessage The away message to use.
     */
    public void goAway(final String awayMessage) {
        new CommandWithToastOnExceptionAsyncTask(context, new Command() {
            @Override
            public void runCommand() throws CommandException {
                controller.goAway(awayMessage);
            }
        }).execute();
    }

    /**
     * Sets the application user as back from away.
     */
    public void comeBack() {
        new CommandWithToastOnExceptionAsyncTask(context, new Command() {
            @Override
            public void runCommand() throws CommandException {
                controller.comeBack();
            }
        }).execute();
    }

    /**
     * Notifies about a new message if the main chat is not visible.
     */
    @Override
    public void notifyMessageArrived(final User user, final String message) {
        if (!isVisible()) {
            notificationService.notifyNewMainChatMessage(user, message);
        }
    }

    /**
     * Notifies about a new message if neither the main chat nor the private chat with
     * the user who sent the message is visible.
     */
    @Override
    public void notifyPrivateMessageArrived(final User user, final String message) {
        Validate.notNull(user, "User can not be null");
        Validate.notNull(user.getPrivchat(), "Private chat can not be null");

        if (!isVisible() && !user.getPrivchat().isVisible()) {
            notificationService.notifyNewPrivateChatMessage(user, message);
        }
    }

    @Override
    public MessageController getMessageController() {
        return msgController;
    }

    @Override
    public void createPrivChat(final User user) {
        Validate.notNull(user, "User can not be null");

        if (user.getPrivchat() == null) {
            user.setPrivchat(new AndroidPrivateChatWindow(context, user));
        }

        if (user.getPrivateChatLogger() == null) {
            user.setPrivateChatLogger(new ChatLogger(user.getNick(), settings, errorHandler));
        }
    }

    @Override
    public boolean isVisible() {
        return mainChatController != null && mainChatController.isVisible();
    }

    @Override
    public boolean isFocused() {
        return isVisible();
    }

    @Override
    public void quit() {

    }

    @Override
    public void appendToChat(final String message, final int color) {
        Validate.notEmpty(message, "Message can not be empty");

        final CharSequence styledMessage = messageStyler.styleAndAppend(message, color);

        if (mainChatController != null) {
            mainChatController.appendToChat(styledMessage);
        }
    }

    @Override
    public void appendImage(final byte[] imageBytes, final String label, final int color) {
        Validate.notNull(imageBytes, "Image bytes can not be null");

        final CharSequence styledMessage = messageStyler.styleAndAppendImage(imageBytes, label, color);

        if (mainChatController != null) {
            mainChatController.appendToChat(styledMessage);
        }
    }

    public void logOn() {
        controller.start();
        controller.logOn();
    }

    public void logOff() {
        final AsyncTask<Void, Void, Void> logOffTask = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(final Void... voids) {
                controller.logOff(false);
                controller.shutdown();
                return null;
            }
        };

        logOffTask.execute();
    }

    public void registerMainChatController(final MainChatController theMainChatController) {
        Validate.notNull(theMainChatController, "MainChatController can not be null");

        mainChatController = theMainChatController;
        mainChatController.updateChat(messageStyler.getHistory());
    }

    public void unregisterMainChatController(final MainChatController theMainChatController) {
        if (this.mainChatController == theMainChatController) {
            this.mainChatController = null;
        }
    }

    public void sendMessage(final String message) {
        Validate.notEmpty(message, "Message can not be empty");

        final AsyncTask<Void, Void, Void> sendMessageTask = new AsyncTask<Void, Void, Void>() {
            @Override
            public Void doInBackground(final Void... voids) {
                try {
                    controller.sendChatMessage(message);
                    msgController.showOwnMessage(message);
                }

                catch (final CommandException e) {
                    msgController.showSystemMessage(e.getMessage());
                }

                return null;
            }
        };

        sendMessageTask.execute();
    }

    public void sendPrivateMessage(final String privateMessage, final User user) {
        Validate.notEmpty(privateMessage, "Private message can not be empty");
        Validate.notNull(user, "User can not be null");

        final AsyncTask<Void, Void, Void> sendMessageTask = new AsyncTask<Void, Void, Void>() {
            @Override
            public Void doInBackground(final Void... voids) {
                try {
                    controller.sendPrivateMessage(privateMessage, user);
                    msgController.showPrivateOwnMessage(user, privateMessage);
                }

                catch (final CommandException e) {
                    msgController.showPrivateSystemMessage(user, e.getMessage());
                }

                return null;
            }
        };

        sendMessageTask.execute();
    }

    public boolean changeNickName(final String nick) {
        final String trimNick = nick.trim();

        if (trimNick.equals(me.getNick())) {
            return false;
        }

        if (!Tools.isValidNick(trimNick)) {
            Toast.makeText(context, context.getString(R.string.error_nick_name_invalid), Toast.LENGTH_LONG).show();
        }

        else if (controller.isNickInUse(trimNick)) {
            Toast.makeText(context, R.string.error_nick_name_in_use, Toast.LENGTH_LONG).show();
        }

        else {
            return doChangeNickName(trimNick);
        }

        return false;
    }

    private boolean doChangeNickName(final String nick) {
        final CommandWithToastOnExceptionAsyncTask changeNickNameTask = new CommandWithToastOnExceptionAsyncTask(context, new Command() {
            @Override
            public void runCommand() throws CommandException {
                controller.changeMyNick(nick);
                msgController.showSystemMessage(context.getString(R.string.message_your_nick_name_changed, me.getNick()));
                showTopic();
            }
        });

        changeNickNameTask.execute();

        try {
            return changeNickNameTask.get();
        }

        // Not sure if anything is going to interrupt this thread in practice
        catch (final InterruptedException e) {
            throw new RuntimeException(String.format("Was interrupted while changing nick name to '%s'", nick), e);
        }

        // Happens if the async task throws any exceptions
        catch (final ExecutionException e) {
            throw new RuntimeException(String.format("Something went wrong changing nick name to '%s'", nick), e);
        }
    }

    /**
     * Gets a user with the specified user code from the controller.
     *
     * @param code The unique code of the user to get.
     * @return The user who was found, or <code>null</code>.
     */
    public User getUser(final int code) {
        return controller.getUser(code);
    }

    /**
     * Resets the new private message field of the user, in addition to the current notification for the user.
     *
     * @param user The user who's private chat got activated.
     */
    public void activatedPrivChat(final User user) {
        if (user.isNewPrivMsg()) {
            user.setNewPrivMsg(false); // In case the user has logged off
            controller.changeNewMessage(user.getCode(), false);
        }

        notificationService.resetPrivateChatNotification(user);
    }

    /**
     * Updates whether the user is currently writing or not. This makes sure a star is shown
     * by the nick name in the user list, and sends a notice to other users so they can show the same thing.
     *
     * @param isCurrentlyWriting If the application user is currently writing.
     */
    public void updateMeWriting(final boolean isCurrentlyWriting) {
        final AsyncTask<Void, Void, Void> updateMeWritingTask = new AsyncTask<Void, Void, Void>() {
            @Override
            public Void doInBackground(final Void... voids) {
                controller.updateMeWriting(isCurrentlyWriting);
                return null;
            }
        };

        updateMeWritingTask.execute();
    }

    /**
     * Resets both private and main chat notifications using the notification service.
     */
    public void resetAllMessageNotifications() {
        notificationService.resetAllMessageNotifications();
    }

    /**
     * Gets the backend user list.
     *
     * @return The user list.
     */
    public UserList getUserList() {
        return userList;
    }

    /**
     * Gets the application user.
     *
     * @return Me.
     */
    public User getMe() {
        return me;
    }

    /**
     * Gets the application settings.
     *
     * @return The settings.
     */
    public AndroidSettings getSettings() {
        return settings;
    }

    /**
     * Sends the file to the specified user. Shows a Toast if it fails.
     *
     * @param user The user to send to.
     * @param file The file to send.
     */
    public void sendFile(final User user, final FileToSend file) {
        new CommandWithToastOnExceptionAsyncTask(context, new Command() {
            @Override
            public void runCommand() throws CommandException {
                commandParser.sendFile(user, file);
                echoImageIfApplicable(user, file);
            }
        }).execute();
    }

    /**
     * Shows a sent image inline, routing it to the private chat window with the
     * recipient if one is open, otherwise to the main chat.
     *
     * <p>The image is detected by attempting to decode the bytes, rather than
     * relying on the file name extension - some Android image providers return
     * a display name without an extension.</p>
     *
     * @param user The recipient of the file.
     * @param file The file that was just sent.
     */
    private void echoImageIfApplicable(final User user, final FileToSend file) {
        try {
            final byte[] imageBytes = AndroidFileUtils.readBytes(file.getInputStream());

            if (imageBytes != null && imageBytes.length > 0
                    && BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length) != null) {
                msgController.showImageMessage(user, me.getNick(), imageBytes);
            }
        }

        catch (final Exception e) {
            // Could not read the image for inline echo; the file is still sent.
        }
    }

    public void registerNetworkConnectionListener(final NetworkConnectionListener listener) {
        controller.registerNetworkConnectionListener(listener);
    }
}
