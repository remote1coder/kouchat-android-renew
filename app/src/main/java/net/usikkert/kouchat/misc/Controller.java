
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

package net.usikkert.kouchat.misc;

import java.util.List;

import net.usikkert.kouchat.Constants;
import net.usikkert.kouchat.autocomplete.AutoCompleter;
import net.usikkert.kouchat.autocomplete.CommandAutoCompleteList;
import net.usikkert.kouchat.autocomplete.UserAutoCompleteList;
import net.usikkert.kouchat.crypto.CryptoManager;
import net.usikkert.kouchat.crypto.Identity;
import net.usikkert.kouchat.crypto.TrustPrompt;
import net.usikkert.kouchat.crypto.TrustedKeys;
import net.usikkert.kouchat.event.NetworkConnectionListener;
import net.usikkert.kouchat.jmx.JMXBeanLoader;
import net.usikkert.kouchat.message.CoreMessages;
import net.usikkert.kouchat.net.AsyncMessageResponderWrapper;
import net.usikkert.kouchat.net.DefaultMessageResponder;
import net.usikkert.kouchat.net.DefaultPrivateMessageResponder;
import net.usikkert.kouchat.net.FileReceiver;
import net.usikkert.kouchat.net.FileSender;
import net.usikkert.kouchat.net.FileToSend;
import net.usikkert.kouchat.net.MessageParser;
import net.usikkert.kouchat.net.MessageResponder;
import net.usikkert.kouchat.net.NetworkMessages;
import net.usikkert.kouchat.net.NetworkService;
import net.usikkert.kouchat.net.PrivateMessageParser;
import net.usikkert.kouchat.net.PrivateMessageResponder;
import net.usikkert.kouchat.net.TransferList;
import net.usikkert.kouchat.settings.NetworkMode;
import net.usikkert.kouchat.settings.Settings;
import net.usikkert.kouchat.settings.SettingsSaver;
import net.usikkert.kouchat.ui.UserInterface;
import net.usikkert.kouchat.util.DateTools;
import net.usikkert.kouchat.util.TimerTools;
import net.usikkert.kouchat.util.Tools;
import net.usikkert.kouchat.util.Validate;

import org.jetbrains.annotations.Nullable;

/**
 * This controller gives access to the network and the state of the
 * application, like the user list and the topic.
 * <br><br>
 * When changing state, use the methods available <strong>here</strong> instead
 * of doing it manually, to make sure the state is consistent.
 * <br><br>
 * To connect to the network, use {@link #logOn()}.
 *
 * @author Christian Ihle
 */
public class Controller implements NetworkConnectionListener, net.usikkert.kouchat.crypto.CryptoMessageSink {

    /** The time to wait after the network is up before logon is set as completed. */
    private static final int LOGON_DELAY = 1500;

    private final DateTools dateTools = new DateTools();
    private final TimerTools timerTools = new TimerTools();

    private final ChatState chatState;
    private final UserListController userListController;
    private final NetworkService networkService;
    private final NetworkMessages networkMessages;
    private final IdleThread idleThread;
    private final TransferList tList;
    private final WaitingList wList;
    private final User me;
    private final UserInterface ui;
    private final MessageController msgController;
    private final Settings settings;
    private final SettingsSaver settingsSaver;
    private final DayTimer dayTimer;
    private final Thread shutdownHook;
    private final CoreMessages coreMessages;
    private final ErrorHandler errorHandler;

    /** The local RSA identity for end-to-end encryption. */
    private final Identity identity;

    /** Persisted trusted peer keys. */
    private final TrustedKeys trustedKeys;

    /** Orchestrates the encrypted handshake and per-peer channels. */
    private final CryptoManager cryptoManager;

    /** User codes for which the "not encrypted" notice has already been shown once. */
    private final java.util.Set<Integer> plaintextNoticed =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Constructor. Initializes the controller.
     *
     * <p>Use {@link #start()} and {@link #logOn()} to connect to the network.</p>
     *
     * @param ui The active user interface object.
     * @param settings The settings to use.
     * @param settingsSaver The saver to use for storing settings.
     * @param coreMessages The core messages to use.
     * @param errorHandler The error handler to use.
     */
    public Controller(final UserInterface ui, final Settings settings, final SettingsSaver settingsSaver,
                      final CoreMessages coreMessages, final ErrorHandler errorHandler) {
        Validate.notNull(ui, "User interface can not be null");
        Validate.notNull(settings, "Settings can not be null");
        Validate.notNull(settingsSaver, "Settings saver can not be null");
        Validate.notNull(coreMessages, "Core messages can not be null");
        Validate.notNull(errorHandler, "Error handler can not be null");

        this.ui = ui;
        this.settings = settings;
        this.settingsSaver = settingsSaver;
        this.coreMessages = coreMessages;
        this.errorHandler = errorHandler;

        shutdownHook = new Thread("ControllerShutdownHook") {
            @Override
            public void run() {
                try {
                    logOff(false);
                }

                finally {
                    doShutdown();
                }
            }
        };

        Runtime.getRuntime().addShutdownHook(shutdownHook);

        me = settings.getMe();
        userListController = new UserListController(settings);
        chatState = new ChatState();
        tList = new TransferList();
        wList = new WaitingList();
        idleThread = new IdleThread(this, ui, settings);
        dayTimer = new DayTimer(ui);
        networkService = new NetworkService(this, settings, errorHandler);
        final MessageResponder msgResponder = new DefaultMessageResponder(this, ui, settings, coreMessages);
        final AsyncMessageResponderWrapper msgResponderWrapper = new AsyncMessageResponderWrapper(msgResponder, this);
        final PrivateMessageResponder privmsgResponder = new DefaultPrivateMessageResponder(this, ui, settings);
        final MessageParser msgParser = new MessageParser(msgResponderWrapper, settings);
        networkService.registerMainChatMessageReceiverListener(msgParser);
        final PrivateMessageParser privmsgParser = new PrivateMessageParser(privmsgResponder, settings);
        networkService.registerPrivateChatReceiverListener(privmsgParser);
        networkMessages = new NetworkMessages(networkService, settings);
        networkService.registerNetworkConnectionListener(this);

        identity = new Identity();
        trustedKeys = new TrustedKeys();
        cryptoManager = new CryptoManager(identity, trustedKeys, this);
        cryptoManager.setLocalCode(me.getCode());

        msgController = ui.getMessageController();

        cryptoManager.setStatusListener(new net.usikkert.kouchat.crypto.CryptoStatusListener() {
            @Override
            public void peerUnsupported(final User peer) {
                // Runs on the crypto timeout thread. Inform the user that this peer is
                // not encrypted, so they never assume encryption is active when it isn't.
                plaintextNoticed.add(peer.getCode());
                msgController.showSystemMessage(coreMessages.getMessage(
                        "core.crypto.systemMessage.peerUnsupported", peer.getNick()));
            }
        });
    }

    /**
     * Starts background threads and shows welcome messages in the user interface.
     */
    public void start() {
        dayTimer.startTimer();
        idleThread.start();

        msgController.showSystemMessage(coreMessages.getMessage("core.startup.systemMessage.welcome",
                                                                Constants.APP_NAME));
        final String date = dateTools.currentDateToString(coreMessages.getMessage("core.dateFormat.today"));
        msgController.showSystemMessage(coreMessages.getMessage("core.startup.systemMessage.todayIs", date));
    }

    /**
     * Gets the current topic.
     *
     * @return The current topic.
     */
    public Topic getTopic() {
        return chatState.getTopic();
    }

    /**
     * Gets the list of online users.
     *
     * @return The user list.
     */
    public UserList getUserList() {
        return userListController.getUserList();
    }

    /**
     * Returns if the application user wrote the last time
     * {@link #changeWriting(int, boolean)} was called.
     *
     * @return If the user wrote.
     * @see ChatState#isWrote()
     */
    public boolean isWrote() {
        return chatState.isWrote();
    }

    /**
     * Updates the write state for the user. This is useful to see which
     * users are currently writing.
     *
     * If the user is the application user, messages will be sent to the
     * other clients to notify of changes.
     *
     * @param code The user code for the user to update.
     * @param writing True if the user is writing.
     */
    public void changeWriting(final int code, final boolean writing) {
        userListController.changeWriting(code, writing);

        if (code == me.getCode()) {
            chatState.setWrote(writing);

            if (writing) {
                networkMessages.sendWritingMessage();
            } else {
                networkMessages.sendStoppedWritingMessage();
            }
        }
    }

    /**
     * Updates whether the user is currently writing or not. This makes sure a star is shown
     * by the nick name in the user list, and sends a notice to other users so they can show the same thing.
     *
     * @param isCurrentlyWriting If the application user is currently writing.
     */
    public void updateMeWriting(final boolean isCurrentlyWriting) {
        if (isCurrentlyWriting) {
            if (!isWrote()) {
                changeWriting(me.getCode(), true);
            }
        }

        else {
            if (isWrote()) {
                changeWriting(me.getCode(), false);
            }
        }
    }

    /**
     * Sets the application user as away with the specified away message.
     *
     * @param awayMessage The away message to use. Can not be empty.
     * @throws CommandException If the away message is empty, or the application user could not be set as away.
     */
    public void goAway(final String awayMessage) throws CommandException {
        if (Tools.isEmpty(awayMessage)) {
            throw new CommandException(coreMessages.getMessage("core.away.error.missingAwayMessage"));
        }

        changeAwayStatus(me.getCode(), true, awayMessage);

        ui.changeAway(true);
        msgController.showSystemMessage(coreMessages.getMessage("core.away.systemMessage.wentAway", me.getAwayMsg()));
    }

    /**
     * Sets the application user as back from away.
     *
     * @throws CommandException If the application user could not be set as back from away.
     */
    public void comeBack() throws CommandException {
        changeAwayStatus(me.getCode(), false, "");

        ui.changeAway(false);
        msgController.showSystemMessage(coreMessages.getMessage("core.away.systemMessage.cameBack"));
    }

    /**
     * Updates the away status and the away message for the user.
     *
     * @param code The user code for the user to update.
     * @param away If the user is away or not.
     * @param awaymsg The away message for that user. Will be trimmed.
     * @throws CommandException If there is no connection to the network,
     *         or the user tries to set an away message that is to long.
     */
    public void changeAwayStatus(final int code, final boolean away, final String awaymsg) throws CommandException {
        if (code == me.getCode() && !isLoggedOn()) {
            throw new CommandException(coreMessages.getMessage("core.away.error.notConnected"));
        } else if (code == me.getCode() && Tools.getBytes(awaymsg) > Constants.MESSAGE_MAX_BYTES) {
            throw new CommandException(coreMessages.getMessage("core.away.error.awayMessageTooLong",
                                                               Constants.MESSAGE_MAX_BYTES));
        }

        final String trimmedAwayMessage = awaymsg.trim();

        if (code == me.getCode()) {
            if (away) {
                networkMessages.sendAwayMessage(trimmedAwayMessage);
            } else {
                networkMessages.sendBackMessage();
            }
        }

        userListController.changeAwayStatus(code, away, trimmedAwayMessage);
    }

    /**
     * Checks if the nick is in use by another user.
     *
     * @param nick The nick to check.
     * @return True if the nick is already in use.
     */
    public boolean isNickInUse(final String nick) {
        return userListController.isNickNameInUse(nick);
    }

    /**
     * Checks if the user with that user code is already in the user list.
     *
     * @param code The user code of the user to check.
     * @return True if the user is not in the user list.
     */
    public boolean isNewUser(final int code) {
        return userListController.isNewUser(code);
    }

    /**
     * Changes the nick for the application user, sends a message over the
     * network to notify the other clients of the change, and saves the changes.
     *
     * @param newNick The new nick for the application user.
     * @throws CommandException If the user is away.
     */
    public void changeMyNick(final String newNick) throws CommandException {
        if (me.isAway()) {
            throw new CommandException(coreMessages.getMessage("core.nick.error.meIsAway"));
        }

        networkMessages.sendNickMessage(newNick);
        changeNick(me.getCode(), newNick);
        saveSettings();
    }

    /**
     * Changes the nick of the user.
     *
     * @param code The user code for the user.
     * @param nick The new nick for the user.
     */
    public void changeNick(final int code, final String nick) {
        userListController.changeNickName(code, nick);
    }

    /**
     * Saves the current settings.
     */
    public void saveSettings() {
        settingsSaver.saveSettings();
    }

    /**
     * Gets the user with the specified user code.
     *
     * @param code The user code for the user.
     * @return The user with the specified user code, or <em>null</em> if not found.
     */
    @Nullable
    public User getUser(final int code) {
        return userListController.getUser(code);
    }

    /**
     * Gets the user with the specified nick name.
     *
     * @param nick The nick name to check for.
     * @return The user with the specified nick name, or <em>null</em> if not found.
     */
    @Nullable
    public User getUser(final String nick) {
        return userListController.getUser(nick);
    }

    /**
     * Sends the necessary network messages to log the user onto the network
     * and query for the users and state.
     */
    private void sendLogOn() {
        networkMessages.sendLogonMessage();
        networkMessages.sendClient();
        networkMessages.sendExposeMessage();
        networkMessages.sendGetTopicMessage();
    }

    /**
     * This should be run after a successful logon, to update the connection state.
     */
    private void runDelayedLogon() {
        timerTools.scheduleTimerTask("DelayedLogonTimer", new DelayedLogonTask(networkService, chatState), LOGON_DELAY);
    }

    /**
     * Logs this client onto the network.
     */
    public void logOn() {
        if (!networkService.isConnectionWorkerAlive()) {
            networkService.connect();
        }
    }

    /**
     * Logs this client off the network.
     *
     * <br /><br />
     *
     * <strong>Note:</strong> removeUsers should not be true when called
     * from a ShutdownHook, as that will lead to a deadlock. See
     * http://bugs.sun.com/bugdatabase/view_bug.do;?bug_id=6261550 for details.
     *
     * @param removeUsers Set to true to remove users from the user list.
     */
    public void logOff(final boolean removeUsers) {
        networkMessages.sendLogoffMessage();
        chatState.setLoggedOn(false);
        chatState.setLogonCompleted(false);
        networkService.disconnect();

        getTopic().resetTopic();

        if (removeUsers) {
            removeAllUsers();
        } else {
            closeAllUserResources();
        }

        me.reset();
    }

    /**
     * Cancels all file transfers, sets all users as logged off,
     * and removes them from the user list.
     */
    private void removeAllUsers() {
        final UserList userList = getUserList();

        for (int i = 0; i < userList.size(); i++) {
            final User user = userList.get(i);

            if (!user.isMe()) {
                removeUser(user, coreMessages.getMessage("core.network.systemMessage.meLogOff"));
                i--;
            }
        }
    }

    /**
     * Removes a user from the user list and cleans up the state. This is done when a user logs off or times out.
     *
     * <p>All file transfers are cancelled, logs are closed, and private chats will be notified with a system message.</p>
     *
     * @param user The user to remove.
     * @param privateSystemMessage The system message to show in the private chat window for that user.
     */
    public void removeUser(final User user, final String privateSystemMessage) {
        final UserList userList = getUserList();

        user.setOnline(false);
        cancelFileTransfers(user);
        userList.remove(user);

        if (user.getPrivchat() != null) {
            msgController.showPrivateSystemMessage(user, privateSystemMessage);
            user.getPrivchat().setLoggedOff();
        }

        closePrivateChatLogger(user);
    }

    private void closeAllUserResources() {
        final UserList userList = getUserList();

        for (int i = 0; i < userList.size(); i++) {
            final User user = userList.get(i);

            cancelFileTransfers(user);
            closePrivateChatLogger(user);
        }
    }

    private void closePrivateChatLogger(final User user) {
        if (user.getPrivateChatLogger() != null) {
            user.getPrivateChatLogger().close();
        }
    }

    /**
     * Cancels all file transfers for that user.
     *
     * @param user The user to cancel for.
     */
    public void cancelFileTransfers(final User user) {
        final List<FileSender> fsList = tList.getFileSenders(user);
        final List<FileReceiver> frList = tList.getFileReceivers(user);

        for (final FileSender fs : fsList) {
            fs.cancel();
            tList.removeFileSender(fs);
        }

        for (final FileReceiver fr : frList) {
            fr.cancel();
            tList.removeFileReceiver(fr);
        }
    }

    /**
     * Prepares the application for shutdown.
     * Should <strong>only</strong> be called when the application shuts down.
     */
    public void shutdown() {
        doShutdown();
        Runtime.getRuntime().removeShutdownHook(shutdownHook); // This throws exception if called from the shutdown hook
    }

    private void doShutdown() {
        idleThread.stopThread();
        dayTimer.stopTimer();
        msgController.shutdown();
    }

    /**
     * Sends a message over the network, asking the other clients to identify
     * themselves.
     */
    public void sendExposeMessage() {
        networkMessages.sendExposeMessage();
    }

    /**
     * Sends an expose message to a specific ip address, to connect to a client
     * that may not be on the same multicast network.
     *
     * @param ipAddress The ip address to connect to.
     */
    public void connectToIp(final String ipAddress) {
        networkService.addP2pPeer(ipAddress);
        networkMessages.sendExposeToIp(ipAddress);
        networkMessages.sendP2pConnect(ipAddress);
        msgController.showSystemMessage("Connecting to " + ipAddress + " (point-to-point)...");
    }

    /**
     * Registers a peer for direct point-to-point (unicast) communication,
     * so subsequent messages bypass multicast.
     *
     * @param ipAddress The ip address of the peer.
     */
    public void addP2pPeer(final String ipAddress) {
        networkService.addP2pPeer(ipAddress);
    }

    /**
     * Removes a peer from direct point-to-point communication.
     *
     * @param ipAddress The ip address of the peer.
     */
    public void removeP2pPeer(final String ipAddress) {
        networkService.removeP2pPeer(ipAddress);
    }

    /**
     * Sends a message over the network to identify this client.
     */
    public void sendExposingMessage() {
        networkMessages.sendExposingMessage();
    }

    /**
     * Sends an exposing message to a specific ip address, responding to a manual ip expose request.
     *
     * @param ipAddress The ip address to send the exposing message to.
     */
    public void sendExposingToIp(final String ipAddress) {
        networkMessages.sendExposingToIp(ipAddress);
    }

    /**
     * Sends a message over the network to ask for the current topic.
     */
    public void sendGetTopicMessage() {
        networkMessages.sendGetTopicMessage();
    }

    /**
     * Sends a message over the network to notify other clients that this
     * client is still alive.
     */
    public void sendIdleMessage() {
        if (isConnected()) {
            networkMessages.sendIdleMessage();
        }
    }

    /**
     * Sends a chat message over the network, to all the other users.
     *
     * @param msg The message to send.
     * @throws CommandException If there is no connection to the network,
     *         or the application user is away,
     *         or the message is empty,
     *         or the message is too long.
     */
    public void sendChatMessage(final String msg) throws CommandException {
        if (!isConnected()) {
            throw new CommandException(coreMessages.getMessage("core.chatMessage.error.notConnected"));
        } else if (me.isAway()) {
            throw new CommandException(coreMessages.getMessage("core.chatMessage.error.meIsAway"));
        } else if (msg.trim().length() == 0) {
            throw new CommandException(coreMessages.getMessage("core.chatMessage.error.emptyMessage"));
        } else if (Tools.getBytes(msg) > Constants.MESSAGE_MAX_BYTES_CHAT) {
            throw new CommandException(coreMessages.getMessage("core.chatMessage.error.messageTooLong",
                                                               Constants.MESSAGE_MAX_BYTES_CHAT));
        } else if (isP2pMode()) {
            // P2P mode: group chat is NOT broadcast. Send encrypted to each trusted peer.
            sendEncryptedChatMessageToPeers(msg);
        } else if (isUnicastMode()) {
            // Unicast mode: group chat is NOT broadcast. Send unencrypted to each online peer.
            sendChatMessageToPeers(msg);
        } else {
            networkMessages.sendChatMessage(msg);
        }
    }

    /**
     * Sends an unencrypted group chat message to every online peer, one unicast
     * per peer. Used in UNICAST mode, where group chat must not be multicast.
     *
     * <p>Messages that fit in a udp packet are sent over udp unicast; longer
     * messages go over the tcp connection to each peer instead.</p>
     *
     * @param msg The message to send.
     * @throws CommandException If no peer is online.
     */
    private void sendChatMessageToPeers(final String msg) throws CommandException {
        final UserList userList = getUserList();
        final boolean fitsUdp = Tools.getBytes(msg) <= Constants.MESSAGE_MAX_BYTES;
        boolean sent = false;

        synchronized (userList) {
            for (int i = 0; i < userList.size(); i++) {
                final User user = userList.get(i);

                if (user.isMe() || !user.isOnline()) {
                    continue;
                }

                if (fitsUdp) {
                    if (networkService.sendChatMessageToIp(msg, user.getIpAddress())) {
                        sent = true;
                    }
                }

                else {
                    networkService.sendMessageToUserViaTcp(msg, user);
                    sent = true;
                }
            }
        }

        if (!sent) {
            throw new CommandException(coreMessages.getMessage("core.chatMessage.error.noOnlinePeers"));
        }
    }

    /**
     * Sends an encrypted group chat message to every peer with an active channel.
     * Used in P2P mode, where group chat must not be multicast.
     *
     * @param msg The message to send.
     * @throws CommandException If no encrypted peer is connected yet.
     */
    private void sendEncryptedChatMessageToPeers(final String msg) throws CommandException {
        final UserList userList = getUserList();
        boolean sent = false;

        synchronized (userList) {
            for (int i = 0; i < userList.size(); i++) {
                final User user = userList.get(i);

                if (user.isMe() || !user.isOnline()) {
                    continue;
                }

                if (cryptoManager.isChannelActive(user)) {
                    final String cipher = cryptoManager.encryptForPeer(user, msg);

                    if (cipher != null) {
                        networkMessages.sendEncryptedChatMessage(user, cipher);
                        sent = true;
                    }
                }
            }
        }

        if (!sent) {
            throw new CommandException(coreMessages.getMessage("core.crypto.systemMessage.noEncryptedPeers"));
        }
    }

    /**
     * Sends a message over the network with the current topic.
     */
    public void sendTopicRequestedMessage() {
        networkMessages.sendTopicRequestedMessage(getTopic());
    }

    /**
     * Changes the topic, and sends a notification to the other clients.
     *
     * @param newTopic The new topic to set.
     * @throws CommandException If there is no connection to the network,
     *         or the application user is away,
     *         or the topic is too long.
     */
    public void changeTopic(final String newTopic) throws CommandException {
        if (!isLoggedOn()) {
            throw new CommandException(coreMessages.getMessage("core.topic.error.notConnected"));
        } else if (me.isAway()) {
            throw new CommandException(coreMessages.getMessage("core.topic.error.meIsAway"));
        } else if (Tools.getBytes(newTopic) > Constants.MESSAGE_MAX_BYTES) {
            throw new CommandException(coreMessages.getMessage("core.topic.error.messageTooLong",
                                                               Constants.MESSAGE_MAX_BYTES));
        }

        final long time = System.currentTimeMillis();
        final Topic newTopicObj = new Topic(newTopic, me.getNick(), time);
        networkMessages.sendTopicChangeMessage(newTopicObj);
        final Topic topic = getTopic();
        topic.changeTopic(newTopicObj);
    }

    /**
     * Sends a message over the network to notify the other clients that
     * a client has tried to logon using the nick name of the
     * application user.
     *
     * @param nick The nick that is already in use by the application user.
     */
    public void sendNickCrashMessage(final String nick) {
        networkMessages.sendNickCrashMessage(nick);
    }

    /**
     * Sends a message over the network to notify the file sender that you
     * aborted the file transfer.
     *
     * @param user The user sending a file.
     * @param fileHash The unique hash code of the file.
     * @param fileName The name of the file.
     */
    public void sendFileAbort(final User user, final int fileHash, final String fileName) {
        networkMessages.sendFileAbort(user, fileHash, fileName);
    }

    /**
     * Sends a message over the network to notify the file sender that you
     * accepted the file transfer.
     *
     * @param user The user sending a file.
     * @param port The port the file sender can connect to on this client
     *             to start the file transfer.
     * @param fileHash The unique hash code of the file.
     * @param fileName The name of the file.
     * @throws CommandException If the message was not sent successfully.
     */
    public void sendFileAccept(final User user, final int port, final int fileHash, final String fileName) throws CommandException {
        networkMessages.sendFileAccept(user, port, fileHash, fileName);
    }

    /**
     * Sends a message over the network to notify another user that the
     * application user wants to send a file.
     *
     * @param user The user asked to receive a file.
     * @param file The file to send.
     * @throws CommandException If the specified user is the application user,
     *                          or there is no connection to the network,
     *                          or the application user is away,
     *                          or the specified user is away,
     *                          or the file name is too long.
     */
    public void sendFile(final User user, final FileToSend file) throws CommandException {
        Validate.notNull(user, "User can not be null");
        Validate.notNull(file, "File can not be null");

        if (user.isMe()) {
            throw new CommandException(coreMessages.getMessage("core.sendFile.error.isMe"));
        } else if (!isConnected()) {
            throw new CommandException(coreMessages.getMessage("core.sendFile.error.notConnected"));
        } else if (me.isAway()) {
            throw new CommandException(coreMessages.getMessage("core.sendFile.error.meIsAway"));
        } else if (user.isAway()) {
            throw new CommandException(coreMessages.getMessage("core.sendFile.error.userIsAway"));
        } else if (Tools.getBytes(file.getName()) > Constants.MESSAGE_MAX_BYTES) {
            throw new CommandException(coreMessages.getMessage("core.sendFile.error.messageTooLong",
                                                               Constants.MESSAGE_MAX_BYTES));
        } else {
            networkMessages.sendFile(user, file);
        }
    }

    /**
     * Gets the list of current transfers.
     *
     * @return The list of transfers.
     */
    public TransferList getTransferList() {
        return tList;
    }

    /**
     * Gets the list of unidentified users.
     *
     * @return The list of unidentified users.
     */
    public WaitingList getWaitingList() {
        return wList;
    }

    /**
     * If any users have timed out because of missed idle messages, then
     * send a message over the network to ask all clients to identify
     * themselves again.
     */
    public void updateAfterTimeout() {
        if (userListController.isTimeoutUsers()) {
            networkMessages.sendExposeMessage();
        }
    }

    /**
     * Sends a message over the network with more information about this client.
     */
    public void sendClientInfo() {
        networkMessages.sendClient();
    }

    /**
     * Sends the client information to a specific ip address over unicast, in response to
     * an expose request from that address. Lets the peer learn our private chat port
     * reliably even if the multicast CLIENT message was lost.
     *
     * @param ipAddress The ip address to send the client info to.
     */
    public void sendClientToIp(final String ipAddress) {
        networkMessages.sendClientToIp(ipAddress);
    }

    /**
     * Sends a private chat message over the network, to the specified user.
     *
     * @param privmsg The private message to send.
     * @param user The user to send the private message to.
     * @throws CommandException If there is no connection to the network,
     *                          or the application user is away,
     *                          or the private message is empty,
     *                          or the private message is too long,
     *                          or the specified user has no port to send the private message to,
     *                          or the specified user is away or offline.
     */
    public void sendPrivateMessage(final String privmsg, final User user) throws CommandException {
        if (!isConnected()) {
            throw new CommandException(coreMessages.getMessage("core.privateChatMessage.error.notConnected"));
        } else if (me.isAway()) {
            throw new CommandException(coreMessages.getMessage("core.privateChatMessage.error.meIsAway"));
        } else if (privmsg.trim().length() == 0) {
            throw new CommandException(coreMessages.getMessage("core.privateChatMessage.error.emptyMessage"));
        } else if (Tools.getBytes(privmsg) > Constants.MESSAGE_MAX_BYTES_CHAT) {
            throw new CommandException(coreMessages.getMessage("core.privateChatMessage.error.messageTooLong",
                                                               Constants.MESSAGE_MAX_BYTES_CHAT));
        } else if (user.isAway()) {
            throw new CommandException(coreMessages.getMessage("core.privateChatMessage.error.userIsAway"));
        } else if (!user.isOnline()) {
            throw new CommandException(coreMessages.getMessage("core.privateChatMessage.error.userIsOffline"));
        } else if (settings.isNoPrivateChat()) {
            throw new CommandException(coreMessages.getMessage("core.privateChatMessage.error.privateChatDisabled"));
        } else if (cryptoManager.isChannelActive(user)) {
            // Encrypted channel active: send the message encrypted over the direct channel.
            // Note: this path unicasts to the peer's main chat port, so the peer's UDP
            // private-chat port is NOT needed - a missed CLIENT message must not block
            // encrypted private chat.
            final String cipher = cryptoManager.encryptForPeer(user, privmsg);

            if (cipher != null) {
                networkMessages.sendEncryptedPrivateMessage(user, cipher, settings.getOwnColor());
            }
        } else if (cryptoManager.isUnsupported(user) || !cryptoManager.hasTrustPrompt()) {
            // Peer does not speak crypto, or no UI to ask for trust (e.g. console): plaintext.
            // This path needs the peer's UDP private-chat port (from its CLIENT message,
            // which can be lost over WiFi multicast). If unknown, ask the peer to re-send
            // its client info over unicast so the next attempt works.
            if (user.getPrivateChatPort() == 0) {
                networkMessages.sendExposeToIp(user.getIpAddress());
                throw new CommandException(coreMessages.getMessage("core.privateChatMessage.error.noPortNumber"));
            }

            // Warn the user once that this conversation is NOT encrypted, so they don't
            // mistakenly believe encryption is active.
            if (plaintextNoticed.add(user.getCode())) {
                msgController.showSystemMessage(coreMessages.getMessage(
                        "core.crypto.systemMessage.plaintextFallback", user.getNick()));
            }
            networkMessages.sendPrivateMessage(privmsg, user);
        } else if (cryptoManager.isRejected(user)) {
            // Trust was declined: encrypted chat is not available with this peer.
            throw new CommandException(coreMessages.getMessage("core.crypto.systemMessage.keyDeclined", user.getNick()));
        } else {
            // Not established yet: start the handshake and ask the user to wait.
            cryptoManager.initiateHandshake(user);
            throw new CommandException(coreMessages.getMessage("core.crypto.systemMessage.waitingForKey", user.getNick()));
        }
    }

    /**
     * Updates if the user has unread private messages for the
     * application user.
     *
     * @param code The user code for the user to update.
     * @param newMsg True if the user has unread private messages.
     */
    public void changeNewMessage(final int code, final boolean newMsg) {
        userListController.changeNewMessage(code, newMsg);
    }

    /**
     * Returns if the client is logged on to the chat and connected to the network.
     *
     * @return True if the client is connected.
     */
    public boolean isConnected() {
        return networkService.isNetworkUp() && isLoggedOn();
    }

    /**
     * Checks the state of the network, and tries to keep the best possible
     * network connection up.
     */
    public void checkNetwork() {
        networkService.checkNetwork();
    }

    /**
     * Returns if the client is logged on to the chat.
     *
     * @return True if the client is logged on to the chat.
     */
    public boolean isLoggedOn() {
        return chatState.isLoggedOn();
    }

    /**
     * Creates a new instance of the {@link AutoCompleter}, with
     * a {@link CommandAutoCompleteList} and a {@link UserAutoCompleteList}.
     *
     * @return A new instance of a ready-to-use AutoCompleter.
     */
    public AutoCompleter getAutoCompleter() {
        final AutoCompleter autoCompleter = new AutoCompleter();
        autoCompleter.addAutoCompleteList(new CommandAutoCompleteList());
        autoCompleter.addAutoCompleteList(new UserAutoCompleteList(getUserList()));

        return autoCompleter;
    }

    @Override
    public void beforeNetworkCameUp() {
        // Nothing to do here
    }

    /**
     * Makes sure the application reacts when the network is available.
     *
     * @param silent If true, wont show the "you are connected..." message to the user.
     */
    @Override
    public void networkCameUp(final boolean silent) {
        // Network came up after a logon
        if (!isLoggedOn()) {
            runDelayedLogon();
            sendLogOn();
        }

        // Network came up after a timeout
        else {
            ui.showTopic();

            if (!silent) {
                msgController.showSystemMessage(coreMessages.getMessage("core.network.systemMessage.connectionBack"));
            }

            networkMessages.sendTopicRequestedMessage(getTopic());
            networkMessages.sendExposingMessage();
            networkMessages.sendGetTopicMessage();
            networkMessages.sendExposeMessage();
            networkMessages.sendIdleMessage();
        }
    }

    /**
     * Makes sure the application reacts when the network is unavailable.
     *
     * @param silent If true, wont show the "you lost contact..." message to the user.
     */
    @Override
    public void networkWentDown(final boolean silent) {
        ui.showTopic();

        if (isLoggedOn()) {
            if (!silent) {
                msgController.showSystemMessage(coreMessages.getMessage("core.network.systemMessage.connectionLost"));
            }
        }

        else {
            msgController.showSystemMessage(coreMessages.getMessage("core.network.systemMessage.meLogOff"));
        }
    }

    /**
     * Gets the chat state.
     *
     * @return The chat state.
     */
    public ChatState getChatState() {
        return chatState;
    }

    /**
     * Creates an instance of a JMX bean loader, and returns it.
     *
     * @return A JMX bean loader.
     */
    public JMXBeanLoader createJMXBeanLoader() {
        return new JMXBeanLoader(this, networkService.getConnectionWorker(), settings, errorHandler);
    }

    public void registerNetworkConnectionListener(final NetworkConnectionListener listener) {
        networkService.registerNetworkConnectionListener(listener);
    }

    // ----- End-to-end encryption -----

    /**
     * Sets the UI callback used to ask the user about trusting a peer's key.
     *
     * @param trustPrompt The trust prompt callback.
     */
    public void setTrustPrompt(final TrustPrompt trustPrompt) {
        cryptoManager.setTrustPrompt(trustPrompt);
    }

    /**
     * Returns the crypto manager (for tests and JMX).
     *
     * @return The crypto manager.
     */
    public CryptoManager getCryptoManager() {
        return cryptoManager;
    }

    /**
     * Whether the client is in P2P (encrypted mesh) mode.
     *
     * @return True if P2P mode.
     */
    public boolean isP2pMode() {
        return settings.getNetworkMode() == NetworkMode.P2P;
    }

    /**
     * Whether the client is in UNICAST (unencrypted mesh) mode.
     *
     * @return True if unicast mode.
     */
    public boolean isUnicastMode() {
        return settings.getNetworkMode() == NetworkMode.UNICAST;
    }

    /**
     * Whether the client uses a unicast mesh (P2P or UNICAST mode), where group
     * chat is sent point-to-point to each peer instead of multicast/broadcast.
     *
     * @return True if a mesh mode is active.
     */
    public boolean isMeshMode() {
        return isP2pMode() || isUnicastMode();
    }

    /**
     * Gets the current network mode.
     *
     * @return The network mode.
     */
    public NetworkMode getNetworkMode() {
        return settings.getNetworkMode();
    }

    /**
     * Switches the network mode and persists it. In the mesh modes (P2P and
     * UNICAST), attempts to mesh with all currently online users.
     *
     * <p>Note: manually registered peers (see {@link #connectToIp(String)}) are kept
     * when switching modes, so such connections keep working.</p>
     *
     * @param mode The new network mode.
     */
    public void setNetworkMode(final NetworkMode mode) {
        settings.setNetworkMode(mode);
        saveSettings();

        if (isMeshMode()) {
            final UserList userList = getUserList();

            synchronized (userList) {
                for (int i = 0; i < userList.size(); i++) {
                    final User user = userList.get(i);

                    if (!user.isMe() && user.isOnline()) {
                        attemptP2pMesh(user);
                    }
                }
            }
        }
    }

    /**
     * Registers a peer for the unicast mesh (so discovery/control messages also reach
     * them directly), and initiates an encrypted handshake if P2P mode is active.
     *
     * @param user The peer to mesh with.
     */
    public void attemptP2pMesh(final User user) {
        if (user == null || user.isMe() || !user.isOnline()) {
            return;
        }

        networkService.addP2pPeer(user.getIpAddress());

        if (isP2pMode() && !cryptoManager.isChannelActive(user) && !cryptoManager.isUnsupported(user)) {
            cryptoManager.initiateHandshake(user);
        }
    }

    /**
     * Starts the encrypted handshake when the user opens a private chat. If the peer
     * turns out to be unsupported (no crypto), private messages fall back to plaintext.
     *
     * @param user The peer.
     */
    public void initiatePrivateChat(final User user) {
        if (user == null || user.isMe()) {
            return;
        }

        if (cryptoManager.isChannelActive(user) || cryptoManager.isUnsupported(user)
                || cryptoManager.isRejected(user)) {
            return;
        }

        // Only show the notice once per pending handshake, not on every chat re-open.
        final boolean alreadyPending = cryptoManager.isHandshakePending(user);

        cryptoManager.initiateHandshake(user);

        // The peer's UDP private-chat port (from its CLIENT message) may be unknown if the
        // multicast copy was lost. Ask the peer to re-send its client info over unicast now,
        // so the plaintext fallback path also works if the peer turns out not to speak crypto.
        if (user.getPrivateChatPort() == 0) {
            networkMessages.sendExposeToIp(user.getIpAddress());
        }

        if (!alreadyPending) {
            msgController.showPrivateSystemMessage(user, coreMessages.getMessage(
                    "core.crypto.systemMessage.waitingForKey", user.getNick()));
        }
    }

    // ----- CryptoMessageSink: delegates to NetworkMessages (TCP unicast) -----

    @Override
    public void sendPubKey(final User peer, final String publicKeyBase64) {
        networkMessages.sendPubKey(peer, publicKeyBase64);
    }

    @Override
    public void sendKeyReq(final User peer) {
        networkMessages.sendKeyReq(peer);
    }

    @Override
    public void sendKeyTrust(final User peer, final String wrappedKey, final String signature) {
        networkMessages.sendKeyTrust(peer, wrappedKey, signature);
    }

    @Override
    public void sendKeyTrustAck(final User peer) {
        networkMessages.sendKeyTrustAck(peer);
    }

    @Override
    public void sendKeyReject(final User peer) {
        networkMessages.sendKeyReject(peer);
    }

    // ----- Responder callbacks: forward network events to the crypto manager -----

    /**
     * A peer's public key arrived. Drives the trust handshake.
     *
     * @param userCode The sender's code.
     * @param publicKeyBase64 The base64 public key.
     */
    public void handlePubKey(final int userCode, final String publicKeyBase64) {
        final User user = getUser(userCode);
        if (user != null) {
            cryptoManager.onPeerPubKey(user, publicKeyBase64);
        }
    }

    /**
     * A peer requested our public key.
     *
     * @param userCode The requester's code.
     */
    public void handleKeyReq(final int userCode) {
        final User user = getUser(userCode);
        if (user != null) {
            cryptoManager.onKeyReq(user);
        }
    }

    /**
     * A peer trusts our key and carries the wrapped session key.
     *
     * @param userCode The sender's code.
     * @param wrappedKey Base64 wrapped AES key.
     * @param signature Base64 signature.
     */
    public void handleKeyTrust(final int userCode, final String wrappedKey, final String signature) {
        final User user = getUser(userCode);
        if (user != null) {
            cryptoManager.onKeyTrust(user, wrappedKey, signature);

            if (cryptoManager.isChannelActive(user)) {
                msgController.showPrivateSystemMessage(user, coreMessages.getMessage(
                        "core.crypto.systemMessage.channelEstablished", user.getNick()));
            }
        }
    }

    /**
     * A peer acknowledged trust back.
     *
     * @param userCode The sender's code.
     */
    public void handleKeyTrustAck(final int userCode) {
        final User user = getUser(userCode);
        if (user != null) {
            cryptoManager.onKeyTrustAck(user);

            if (cryptoManager.isChannelActive(user)) {
                msgController.showPrivateSystemMessage(user, coreMessages.getMessage(
                        "core.crypto.systemMessage.channelEstablished", user.getNick()));
            }
        }
    }

    /**
     * A peer declined to trust our key.
     *
     * @param userCode The sender's code.
     */
    public void handleKeyReject(final int userCode) {
        final User user = getUser(userCode);
        if (user != null) {
            cryptoManager.onKeyReject(user);
            msgController.showPrivateSystemMessage(user, coreMessages.getMessage(
                    "core.crypto.systemMessage.keyDeclined", user.getNick()));
        }
    }

    /**
     * An encrypted private message arrived. Decrypts and shows it.
     *
     * @param userCode The sender's code.
     * @param ciphertextBase64 The base64 ciphertext.
     * @param color The sender's chosen color.
     */
    public void handleEncryptedPrivateMessage(final int userCode, final String ciphertextBase64, final int color) {
        final User user = getUser(userCode);
        if (user == null) {
            return;
        }

        final String plaintext = cryptoManager.decryptFromPeer(user, ciphertextBase64);
        if (plaintext != null) {
            msgController.showPrivateUserMessage(user, plaintext, color);
            ui.notifyPrivateMessageArrived(user, plaintext);
        }
    }

    /**
     * An encrypted group chat message arrived (P2P mode). Decrypts and shows it.
     *
     * @param userCode The sender's code.
     * @param ciphertextBase64 The base64 ciphertext.
     */
    public void handleEncryptedChatMessage(final int userCode, final String ciphertextBase64) {
        final User user = getUser(userCode);
        if (user == null) {
            return;
        }

        final String plaintext = cryptoManager.decryptFromPeer(user, ciphertextBase64);
        if (plaintext != null) {
            msgController.showUserMessage(user.getNick(), plaintext, settings.getOwnColor());
            ui.notifyMessageArrived(user, plaintext);
        }
    }
}
