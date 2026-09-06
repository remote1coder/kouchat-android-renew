
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

import static net.usikkert.kouchat.net.NetworkUtils.IPTOS_RELIABILITY;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.usikkert.kouchat.Constants;
import net.usikkert.kouchat.misc.ErrorHandler;
import net.usikkert.kouchat.util.Validate;

import org.jetbrains.annotations.Nullable;

/**
 * This is the class that sends multicast messages over the network.
 *
 * @author Christian Ihle
 */
public class MessageSender {

    /** The logger. */
    private static final Logger LOG = Logger.getLogger(MessageSender.class.getName());

    /** Fallback broadcast address, used when the subnet broadcast address can not be determined. */
    private static final String LIMITED_BROADCAST_ADDRESS = "255.255.255.255";

    /** The multicast socket used for sending messages. */
    @Nullable
    private MulticastSocket mcSocket;

    /** The inetaddress object with the multicast ip address to send messages to. */
    private InetAddress address;

    /** If connected to the network or not. */
    private boolean connected;

    /** The port to send messages to. */
    private final int port;

    /**
     * Default constructor.
     *
     * <p>Initializes the network with the default ip address and port.</p>
     *
     * @see Constants#NETWORK_IP
     * @see Constants#NETWORK_CHAT_PORT
     * @param errorHandler The error handler to use.
     */
    public MessageSender(final ErrorHandler errorHandler) {
        this(Constants.NETWORK_IP, Constants.NETWORK_CHAT_PORT, errorHandler);
    }

    /**
     * Alternative constructor.
     *
     * <p>Initializes the network with the given ip address and port.</p>
     *
     * @param ipAddress Multicast ip address to connect to.
     * @param port Port to connect to.
     * @param errorHandler The error handler to use.
     */
    public MessageSender(final String ipAddress, final int port, final ErrorHandler errorHandler) {
        LOG.fine("Creating MessageSender on " + ipAddress + ":" + port);

        Validate.notEmpty(ipAddress, "IP address can not be empty");
        Validate.notNull(errorHandler, "Error handler can not be null");

        this.port = port;

        try {
            address = InetAddress.getByName(ipAddress);
        }

        catch (final IOException e) {
            LOG.log(Level.SEVERE, e.toString(), e);

            errorHandler.showCriticalError("Failed to initialize the network:\n" + e + "\n" +
                    Constants.APP_NAME + " will now shutdown.");

            System.exit(1);
        }
    }

    /**
     * Sends a multicast packet to other clients over the network.
     *
     * @param message The message to send in the packet.
     * @return If the message was sent or not.
     * @see Constants#MESSAGE_CHARSET
     * @see Constants#NETWORK_PACKET_SIZE
     */
    public synchronized boolean send(final String message) {
        if (connected) {
            try {
                final byte[] encodedMsg = message.getBytes(Constants.MESSAGE_CHARSET);
                final int size = encodedMsg.length;

                if (size > Constants.NETWORK_PACKET_SIZE) {
                    LOG.log(Level.WARNING, "Message was " + size + " bytes, which is too large.\n" +
                            " The receiver might not get the complete message.\n'" + message + "'");
                }

                final DatagramPacket packet = new DatagramPacket(encodedMsg, size, address, port);
                mcSocket.send(packet);
                LOG.log(Level.FINE, "Sent message: " + message);

                return true;
            }

            catch (final IOException e) {
                LOG.log(Level.WARNING, "Could not send message: " + message, e);
            }
        }

        return false;
    }

    /**
     * Sends a unicast packet to a specific ip address, on the same port as the multicast messages.
     * Used for connecting to a specific user by IP address (manual IP connection).
     *
     * @param message The message to send.
     * @param ipAddress The ip address to send the message to.
     * @return If the message was sent or not.
     */
    public synchronized boolean send(final String message, final String ipAddress) {
        if (connected) {
            try {
                final byte[] encodedMsg = message.getBytes(Constants.MESSAGE_CHARSET);
                final int size = encodedMsg.length;
                final InetAddress targetAddress = InetAddress.getByName(ipAddress);
                final DatagramPacket packet = new DatagramPacket(encodedMsg, size, targetAddress, port);
                mcSocket.send(packet);
                LOG.log(Level.FINE, "Sent unicast message to " + ipAddress + ": " + message);
                return true;
            }

            catch (final IOException e) {
                LOG.log(Level.WARNING, "Could not send unicast message to " + ipAddress + ": " + message, e);
            }
        }

        else {
            // Not connected to multicast, but can still send unicast via a temp socket.
            // This enables manual IP connection even when multicast discovery fails.
            try {
                final byte[] encodedMsg = message.getBytes(Constants.MESSAGE_CHARSET);
                final int size = encodedMsg.length;
                final InetAddress targetAddress = InetAddress.getByName(ipAddress);
                final DatagramPacket packet = new DatagramPacket(encodedMsg, size, targetAddress, port);
                final java.net.DatagramSocket tempSocket = new java.net.DatagramSocket();
                tempSocket.send(packet);
                tempSocket.close();
                LOG.log(Level.FINE, "Sent unicast message to " + ipAddress + " (via temp socket): " + message);
                return true;
            }

            catch (final IOException e) {
                LOG.log(Level.WARNING, "Could not send unicast message to " + ipAddress + ": " + message, e);
            }
        }

        return false;
    }

    /**
     * Sends a broadcast packet to the local subnet, on the same port as the multicast messages.
     * Used in broadcast network mode, where main chat goes over subnet broadcast instead
     * of the multicast group.
     *
     * @param message The message to send.
     * @return If the message was sent or not.
     */
    public synchronized boolean sendBroadcast(final String message) {
        if (!connected || mcSocket == null) {
            return false;
        }

        try {
            final byte[] encodedMsg = message.getBytes(Constants.MESSAGE_CHARSET);
            final int size = encodedMsg.length;

            if (size > Constants.NETWORK_PACKET_SIZE) {
                LOG.log(Level.WARNING, "Message was " + size + " bytes, which is too large.\n" +
                        " The receiver might not get the complete message.\n'" + message + "'");
            }

            final InetAddress broadcastAddress = getBroadcastAddress();
            mcSocket.setBroadcast(true);
            final DatagramPacket packet = new DatagramPacket(encodedMsg, size, broadcastAddress, port);
            mcSocket.send(packet);
            LOG.log(Level.FINE, "Sent broadcast message to " + broadcastAddress.getHostAddress() + ": " + message);

            return true;
        }

        catch (final IOException e) {
            LOG.log(Level.WARNING, "Could not send broadcast message: " + message, e);
        }

        return false;
    }

    /**
     * Finds the subnet broadcast address of the network interface the socket sends on.
     * Falls back to the limited broadcast address (255.255.255.255) if it can not
     * be determined.
     *
     * @return The broadcast address to use.
     */
    private InetAddress getBroadcastAddress() {
        try {
            final NetworkInterface networkInterface = mcSocket != null ? mcSocket.getNetworkInterface() : null;

            if (networkInterface != null) {
                for (final InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                    final InetAddress broadcast = interfaceAddress.getBroadcast();

                    if (broadcast != null) {
                        return broadcast;
                    }
                }
            }
        }

        catch (final IOException e) {
            LOG.log(Level.FINE, "Could not get network interface for broadcast address: " + e.toString());
        }

        try {
            return InetAddress.getByName(LIMITED_BROADCAST_ADDRESS);
        }

        catch (final IOException e) {
            // Should never happen, 255.255.255.255 is always resolvable.
            throw new RuntimeException(e);
        }
    }

    /**
     * Connects to the network with the given network interface, or gives
     * the control to the operating system to choose if <code>null</code>
     * is given.
     *
     * @param networkInterface The network interface to use, or <code>null</code>.
     * @return If connected to the network or not.
     */
    public synchronized boolean startSender(@Nullable final NetworkInterface networkInterface) {
        LOG.log(Level.FINE, "Connecting to " + address.getHostAddress() + ":" + port + " on " + networkInterface);

        try {
            if (connected) {
                LOG.log(Level.FINE, "Already connected.");
            }

            else {
                if (mcSocket == null) {
                    // Bind to an ephemeral local port. The MessageReceiver owns port 40556 for
                    // reception; if the sender also bound 40556, incoming unicast could be
                    // delivered to the sender (which never reads), starving the receiver.
                    mcSocket = new MulticastSocket(0);
                }

                if (networkInterface != null) {
                    mcSocket.setNetworkInterface(networkInterface);
                }

                mcSocket.setTrafficClass(IPTOS_RELIABILITY);
                mcSocket.setTimeToLive(64);
                // Note: the sender does not join the multicast group. Joining is only needed
                // for receiving, which is the MessageReceiver's job. The sender only sends.

                connected = true;
                LOG.log(Level.FINE, "Sender connected (ephemeral local port) sending to port " + port);
            }
        }

        catch (final IOException e) {
            LOG.log(Level.SEVERE, "Could not start sender: " + e.toString(), e);

            if (mcSocket != null) {
                if (!mcSocket.isClosed()) {
                    mcSocket.close();
                }

                mcSocket = null;
            }
        }

        return connected;
    }

    /**
     * Disconnects from the network and closes the multicast socket.
     */
    public synchronized void stopSender() {
        LOG.log(Level.FINE, "Disconnecting from " + address.getHostAddress() + ":" + port);

        if (!connected) {
            LOG.log(Level.FINE, "Not connected.");
        }

        else {
            connected = false;

            // Sender does not join the multicast group, so no leaveGroup needed.

            if (!mcSocket.isClosed()) {
                mcSocket.close();
                mcSocket = null;
            }

            LOG.log(Level.FINE, "Disconnected from " + address.getHostAddress() + ":" + port);
        }
    }
}
