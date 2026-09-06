
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

package net.usikkert.kouchat.net.tcp;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

import net.usikkert.kouchat.Constants;
import net.usikkert.kouchat.util.Logger;
import net.usikkert.kouchat.util.Validate;

import org.jetbrains.annotations.Nullable;

/**
 * Client for communicating over a tcp socket.
 *
 * <p>Each message is framed as a 4-byte length (number of bytes in the utf-8
 * encoding of the message) followed by the utf-8 bytes, so messages can be
 * far larger than the 64k limit of {@link java.io.DataOutput#writeUTF(String)}.
 * The length is capped at {@link Constants#MESSAGE_MAX_BYTES_CHAT} to protect
 * against corrupted frames allocating huge buffers.</p>
 *
 * @author Christian Ihle
 */
public class TCPClient implements Runnable {

    private static final Logger LOG = Logger.getLogger(TCPClient.class);

    private final Socket socket;

    @Nullable
    private DataInputStream inputStream;

    @Nullable
    private DataOutputStream outputStream; // TODO how is this outside of Java?

    @Nullable
    private TCPClientListener clientListener;

    private volatile boolean connected;
    private volatile boolean disconnecting;

    public TCPClient(final Socket socket) {
        Validate.notNull(socket, "Socket can not be null");
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            while (connected && inputStream != null) {
                final String message = readMessage();
                LOG.fine("Message arrived from %s: %s", getIPAddress(), message);

                if (clientListener != null) {
                    clientListener.messageArrived(message, this);
                }
            }
        }

        catch (final IOException e) {
            LOG.severe(e.toString());
            connected = false;

            if (clientListener != null) {
                clientListener.disconnected(this);
            }
        }
    }

    public void send(final String message) {
        if (!connected || outputStream == null) {
            return;
        }

        try {
            writeMessage(message);
            LOG.fine("Sent message: %s", message);
        }

        catch (final IOException e) {
            LOG.severe(e.toString());
            connected = false;

            if (clientListener != null) {
                clientListener.disconnected(this);
            }
        }
    }

    /**
     * Writes one framed message: a 4-byte length followed by the utf-8 bytes.
     *
     * @param message The message to write.
     * @throws IOException If the message could not be written, or it is too large.
     */
    private void writeMessage(final String message) throws IOException {
        final byte[] bytes = message.getBytes(Constants.MESSAGE_CHARSET);

        if (bytes.length > Constants.MESSAGE_MAX_BYTES_CHAT) {
            throw new IOException("Message is " + bytes.length + " bytes, which is over the limit of "
                    + Constants.MESSAGE_MAX_BYTES_CHAT + " bytes");
        }

        outputStream.writeInt(bytes.length);
        outputStream.write(bytes);
        outputStream.flush();
    }

    /**
     * Reads one framed message: a 4-byte length followed by the utf-8 bytes.
     *
     * @return The message that was read.
     * @throws IOException If the message could not be read, or the frame is invalid.
     */
    private String readMessage() throws IOException {
        final int length = inputStream.readInt();

        if (length < 0 || length > Constants.MESSAGE_MAX_BYTES_CHAT) {
            throw new IOException("Invalid message length in tcp frame: " + length);
        }

        final byte[] bytes = new byte[length];
        inputStream.readFully(bytes);

        return new String(bytes, Constants.MESSAGE_CHARSET);
    }

    public boolean connect() {
        try {
            inputStream = new DataInputStream(socket.getInputStream());
            outputStream = new DataOutputStream(socket.getOutputStream());

            LOG.fine("Connected to %s:%s", getIPAddress(), socket.getPort());

            connected = true;
            new Thread(this, getClass().getSimpleName()).start();

            return true;
        }

        catch (final IOException e) {
            LOG.severe(e.getMessage());
            return false;
        }
    }

    public void disconnect() {
        try {
            LOG.fine("Disconnected from %s:%s", getIPAddress(), socket.getPort());
            connected = false;
            disconnecting = true;

            final TCPClientListener listener = clientListener;
            clientListener = null;

            if (listener != null) {
                listener.disconnected(this);
            }

            socket.close();
        }

        catch (final IOException e) {
            LOG.warning(e.getMessage());
        }
    }

    public String getIPAddress() {
        return socket.getInetAddress().getHostAddress();
    }

    public void registerClientListener(@Nullable final TCPClientListener theClientListener) {
        this.clientListener = theClientListener;
    }

    public void setDisconnecting(final boolean isDisconnecting) {
        disconnecting = isDisconnecting;
    }

    public boolean isDisconnecting() {
        return disconnecting;
    }

    public boolean isConnected() {
        return connected;
    }
}
