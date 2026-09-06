
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

package net.usikkert.kouchat.net;

import java.net.NetworkInterface;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import net.usikkert.kouchat.Constants;
import net.usikkert.kouchat.event.NetworkConnectionListener;
import net.usikkert.kouchat.event.ReceiverListener;
import net.usikkert.kouchat.misc.Controller;
import net.usikkert.kouchat.misc.ErrorHandler;
import net.usikkert.kouchat.misc.User;
import net.usikkert.kouchat.net.tcp.TCPNetworkService;
import net.usikkert.kouchat.settings.NetworkMode;
import net.usikkert.kouchat.settings.Settings;
import net.usikkert.kouchat.util.Tools;
import net.usikkert.kouchat.util.Validate;

/**
 * This class has services for connecting to the network.
 *
 * @author Christian Ihle
 */
public class NetworkService implements NetworkConnectionListener {

    /** The logger. */
    private static final Logger LOG = Logger.getLogger(NetworkService.class.getName());

    /** The thread responsible for keeping the network connection up. */
    private final ConnectionWorker connectionWorker;

    /** The multicast message sender. */
    private final MessageSender messageSender;

    /** The multicast message receiver. */
    private final MessageReceiver messageReceiver;

    /** The private message sender. */
    private final UDPSender udpSender;

    /** The private message receiver. */
    private final UDPReceiver udpReceiver;

    /** The network service for tcp connections. */
    private final TCPNetworkService tcpNetworkService;

    /** Proxy for deduplicating multicast and tcp messages. */
    private final MessageDeduplicator messageDeduplicator;

    /** Ip addresses of peers connected via direct point-to-point unicast, bypassing multicast. */
    private final Set<String> p2pPeers = ConcurrentHashMap.newKeySet();

    /** If private chat should be enabled. */
    private final boolean privateChatEnabled;

    /** The settings, used to look up the current network mode when sending. */
    private final Settings settings;

    /**
     * Constructor.
     *
     * @param controller The controller to use.
     * @param settings The settings to use.
     * @param errorHandler The error handler to use.
     */
    public NetworkService(final Controller controller, final Settings settings, final ErrorHandler errorHandler) {
        Validate.notNull(controller, "Controller can not be null");
        Validate.notNull(settings, "Settings can not be null");
        Validate.notNull(errorHandler, "Error handler can not be null");

        LOG.fine("Initializing network");

        this.settings = settings;
        privateChatEnabled = !settings.isNoPrivateChat();

        messageReceiver = new MessageReceiver(errorHandler);
        messageSender = new MessageSender(errorHandler);
        connectionWorker = new ConnectionWorker(settings, errorHandler);
        tcpNetworkService = new TCPNetworkService(controller, settings, errorHandler);
        messageDeduplicator = new MessageDeduplicator();

        if (privateChatEnabled) {
            udpReceiver = new UDPReceiver(settings, errorHandler);
            udpSender = new UDPSender(errorHandler);
        }

        else {
            LOG.fine("Private chat is disabled");
            udpReceiver = null;
            udpSender = null;
        }

        connectionWorker.registerNetworkConnectionListener(this);
    }

    /**
     * Starts the thread responsible for connecting to the network.
     */
    public void connect() {
        connectionWorker.start();
    }

    /**
     * Stops the thread responsible for connecting to the network.
     */
    public void disconnect() {
        connectionWorker.stop();
    }

    /**
     * Gets the connection worker.
     *
     * @return The connection worker.
     */
    public ConnectionWorker getConnectionWorker() {
        return connectionWorker;
    }

    /**
     * Checks if the connection thread is alive.
     *
     * @return If the connection thread is alive.
     */
    public boolean isConnectionWorkerAlive() {
        return connectionWorker.isAlive();
    }

    /**
     * Checks if the network is up.
     *
     * @return If the network is up.
     */
    public boolean isNetworkUp() {
        return connectionWorker.isNetworkUp();
    }

    /**
     * Registers the listener as a connection listener.
     *
     * @param listener The listener to register.
     */
    public void registerNetworkConnectionListener(final NetworkConnectionListener listener) {
        connectionWorker.registerNetworkConnectionListener(listener);
    }

    /**
     * Register a listener for incoming main chat messages from the network.
     *
     * @param listener The listener to register.
     */
    public void registerMainChatMessageReceiverListener(final ReceiverListener listener) {
        messageDeduplicator.registerMainChatReceiverListener(listener);
        messageReceiver.registerReceiverListener(messageDeduplicator);
        tcpNetworkService.registerReceiverListener(messageDeduplicator);
    }

    /**
     * Register a listener for incoming private chat messages from the network.
     *
     * @param listener The listener to register.
     */
    public void registerPrivateChatReceiverListener(final ReceiverListener listener) {
        if (privateChatEnabled) {
            messageDeduplicator.registerPrivateChatReceiverListener(listener);
            udpReceiver.registerReceiverListener(messageDeduplicator);
        }
    }

    /**
     * Send a message to all users.
     *
     * <p>Messages that fit in a udp packet are sent over both udp (multicast or
     * broadcast, depending on the mode) and tcp. Longer messages are sent over
     * tcp only, since a udp datagram can not carry them.</p>
     *
     * @param message The message to send.
     * @return If the message was sent or not.
     */
    public boolean sendMessageToAllUsers(final String message) {
        if (isUdpSize(message)) {
            boolean sent = false;

            // Unicast directly to P2P peers (bypasses multicast filtering by WiFi access points).
            for (final String ip : p2pPeers) {
                sent |= messageSender.send(message, ip);
            }

            // Also send via TCP, and via multicast or subnet broadcast depending on the mode.
            tcpNetworkService.sendMessageToAll(message);

            if (settings.getNetworkMode() == NetworkMode.BROADCAST) {
                // Broadcast mode: subnet broadcast instead of the multicast group.
                sent |= messageSender.sendBroadcast(message);
            }

            else {
                sent |= messageSender.send(message);
            }

            return sent;
        }

        else {
            // Long message: too big for a udp datagram, so tcp only. Delivery can not
            // be confirmed per peer, so the message counts as sent when the network is up.
            tcpNetworkService.sendMessageToAll(message);

            return isNetworkUp();
        }
    }

    /**
     * Send a unicast message to a specific ip address on the main chat port.
     * Used for manual ip connection.
     *
     * @param message The message to send.
     * @param ipAddress The ip address to send the message to.
     * @return If the message was sent or not.
     */
    public boolean sendMessageToIp(final String message, final String ipAddress) {
        return messageSender.send(message, ipAddress);
    }

    /**
     * Send a message to a single user.
     *
     * <p>Messages that fit in a udp packet are sent over both udp unicast and tcp.
     * Longer messages are sent over tcp only, since a udp datagram can not carry them.</p>
     *
     * @param message The message to send.
     * @param user The user to send the message to.
     * @return If the message was sent or not.
     */
    public boolean sendMessageToUser(final String message, final User user) {
        if (!privateChatEnabled) {
            return false;
        }

        if (isUdpSize(message)) {
            tcpNetworkService.sendMessageToUser(message, user);
            return udpSender.send(message, user.getIpAddress(), user.getPrivateChatPort());
        }

        else {
            // Long message: too big for a udp datagram, so tcp only.
            tcpNetworkService.sendMessageToUser(message, user);

            return isNetworkUp();
        }
    }

    /**
     * Checks if a message is small enough to be sent in a single udp datagram.
     *
     * @param message The message to check.
     * @return True if the message fits in a udp packet.
     */
    private boolean isUdpSize(final String message) {
        return Tools.getBytes(message) <= Constants.MESSAGE_MAX_BYTES;
    }

    /**
     * Send a unicast message to a single user via TCP only.
     *
     * <p>Used for the encryption handshake and encrypted private messages, whose
     * base64 payloads (RSA public key, wrapped session key, ciphertext) exceed the
     * UDP packet limit and must not be multicast.</p>
     *
     * @param message The message to send.
     * @param user The user to send the message to.
     * @return If the message was sent or not.
     */
    public boolean sendMessageToUserViaTcp(final String message, final User user) {
        tcpNetworkService.sendMessageToUser(message, user);
        return true;
    }

    /**
     * Sends an encrypted group-chat message to a specific peer over the main-chat
     * unicast path (used in P2P mode, one per trusted peer).
     *
     * @param message The message to send.
     * @param ipAddress The ip address to send the message to.
     * @return If the message was sent or not.
     */
    public boolean sendChatMessageToIp(final String message, final String ipAddress) {
        return messageSender.send(message, ipAddress);
    }

    /**
     * Registers a peer for direct point-to-point (unicast) communication.
     *
     * <p>Once at least one peer is registered, {@link #sendMessageToAllUsers(String)}
     * sends messages via unicast to these peers instead of multicast, bypassing
     * networks where multicast is filtered (e.g. some WiFi access points).</p>
     *
     * @param ipAddress The ip address of the peer.
     */
    public void addP2pPeer(final String ipAddress) {
        Validate.notNull(ipAddress, "IP address can not be null");
        p2pPeers.add(ipAddress);
        LOG.fine("Added P2P peer " + ipAddress + "; peers=" + p2pPeers);
    }

    /**
     * Removes a peer from direct point-to-point communication.
     *
     * @param ipAddress The ip address of the peer.
     */
    public void removeP2pPeer(final String ipAddress) {
        p2pPeers.remove(ipAddress);
        LOG.fine("Removed P2P peer " + ipAddress + "; peers=" + p2pPeers);
    }

    /**
     * Removes all peers from direct point-to-point communication.
     *
     * <p>Used when switching to a network mode that does not use a unicast mesh,
     * so messages are not still unicast to stale peers.</p>
     */
    public void clearP2pPeers() {
        p2pPeers.clear();
        LOG.fine("Cleared all P2P peers");
    }

    /**
     * Checks if there are any peers registered for direct point-to-point communication.
     *
     * @return True if at least one peer is registered.
     */
    public boolean hasP2pPeers() {
        return !p2pPeers.isEmpty();
    }

    /**
     * Checks the state of the network, and tries to keep the best possible
     * network connection up.
     */
    public void checkNetwork() {
        connectionWorker.checkNetwork();
    }

    /**
     * Stops all senders and receivers.
     *
     * {@inheritDoc}
     */
    @Override
    public void networkWentDown(final boolean silent) {
        if (privateChatEnabled) {
            udpSender.stopSender();
            udpReceiver.stopReceiver();
        }

        messageSender.stopSender();
        messageReceiver.stopReceiver();
        tcpNetworkService.stopService();
    }

    @Override
    public void beforeNetworkCameUp() {
        // Nothing to do here
    }

    /**
     * Starts all senders and receivers.
     *
     * {@inheritDoc}
     */
    @Override
    public void networkCameUp(final boolean silent) {
        if (privateChatEnabled) {
            udpSender.startSender();
            udpReceiver.startReceiver();
        }

        final NetworkInterface currentNetworkInterface = connectionWorker.getCurrentNetworkInterface();
        messageSender.startSender(currentNetworkInterface);
        messageReceiver.startReceiver(currentNetworkInterface);
        tcpNetworkService.startService();
    }
}
