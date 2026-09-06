
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

import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.usikkert.kouchat.misc.Controller;
import net.usikkert.kouchat.misc.User;
import net.usikkert.kouchat.misc.UserList;
import net.usikkert.kouchat.settings.Settings;
import net.usikkert.kouchat.util.Logger;
import net.usikkert.kouchat.util.Tools;
import net.usikkert.kouchat.util.Validate;

import org.jetbrains.annotations.Nullable;

/**
 * Handles all the tcp connections.
 *
 * @author Christian Ihle
 */
public class TCPConnectionHandler implements TCPConnectionListener, TCPReceiverListener, Runnable {

    private static final Logger LOG = Logger.getLogger(TCPConnectionHandler.class);

    private final Controller controller;
    private final Settings settings;
    private final ExecutorService executorService;
    private final Map<User, TCPUserClient> userClients;

    @Nullable
    private volatile TCPReceiverListener listener;

    private volatile boolean connected;

    public TCPConnectionHandler(final Controller controller, final Settings settings) {
        Validate.notNull(controller, "Controller can not be null");
        Validate.notNull(settings, "Settings can not be null");

        this.controller = controller;
        this.settings = settings;
        this.executorService = Executors.newCachedThreadPool();
        this.userClients = new ConcurrentHashMap<>();

        final Thread monitorThread = new Thread(this, TCPConnectionHandler.class.getSimpleName());
        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    @Override
    public void socketAdded(final Socket socket) {
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                LOG.fine("Add socket start");

                final TCPClient client = new TCPClient(socket);
                final TCPUserIdentifier userIdentifier = new TCPUserIdentifier(controller, settings, client);

                if (!client.connect()) {
                    LOG.warning("Add socket done. Connection failed.");
                    client.disconnect();
                    return;
                }

                final User user = userIdentifier.waitForUser();

                if (user == null) {
                    LOG.warning("Add socket done. No user found.");
                    client.disconnect();
                    return;
                }

                addClient(user, client);

                LOG.fine("Add socket done. user=%s", user.getNick());
            }
        });
    }

    public void userAdded(final User user) {
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                LOG.fine("Add user start for user=%s", user.getNick());

                if (userAddedAndConnected(user)) {
                    LOG.fine("Add user done. Already added. user=%s", user.getNick());
                    return;
                }

                final TCPConnector tcpConnector = new TCPConnector(user);
                final Socket socket = tcpConnector.connect();

                if (socket == null) {
                    LOG.warning("Add user done. Unable to connect using tcp. Giving up."); // Never tries again
                    return;
                }

                final TCPClient client = new TCPClient(socket);

                if (!client.connect()) {
                    LOG.warning("Add user done. Connection failed. Giving up."); // Never tries again
                    client.disconnect();
                    return;
                }

                addClient(user, client);
                client.send("SYS-IDENTIFY:" + settings.getMe().getCode() + ":" + user.getCode());

                LOG.fine("Add user done for user=%s", user.getNick());
            }
        });
    }

    public void userRemoved(final User user) {
        final TCPUserClient userClient = userClients.remove(user);

        if (userClient != null) {
            userClient.disconnect();
        }
    }

    public void connect() {
        connected = true;
    }

    public void disconnect() {
        connected = false;

        for (final TCPUserClient userClient : userClients.values()) {
            userClient.disconnect();
        }

        userClients.clear();
    }

    private void addClient(final User user, final TCPClient client) {
        // Synchronized: incoming (socketAdded) and outgoing (userAdded) connections for the
        // same user can be set up concurrently, and the check-then-act below would otherwise
        // create two TCPUserClient objects for one user. The orphaned object would later
        // clear the user's tcp-enabled flag while the live connection is still in use.
        synchronized (userClients) {
            final TCPUserClient userClient = userClients.get(user);

            if (userClient == null) {
                userClients.put(user, new TCPUserClient(client, user, this));
            } else {
                userClient.add(client);
            }
        }
    }

    public void sendMessageToAll(final String message) {
        for (final TCPUserClient userClient : userClients.values()) {
            userClient.send(message);
        }
    }

    public void sendMessageToUser(final String message, final User user) {
        final TCPUserClient userClient = userClients.get(user);

        if (userClient != null) {
            userClient.send(message);
        }
    }

    public void registerReceiverListener(final TCPReceiverListener theListener) {
        this.listener = theListener;
    }

    @Override
    public void messageArrived(final String message, final String ipAddress, final User user) {
        if (listener != null) {
            listener.messageArrived(message, ipAddress, user);
        }
    }

    private boolean userAddedAndConnected(final User user) {
        final TCPUserClient userClient = userClients.get(user);

        return userClient != null && userClient.getClientCount() > 0;
    }

    @Override
    public void run() {
        while (true) {
            Tools.sleep(15_000);

            if (!connected) {
                continue;
            }

            try {
                for (final User user : userClients.keySet()) {
                    final TCPUserClient userClient = userClients.get(user);
                    final int clientCount = userClient.getClientCount();

                    if (clientCount == 0) {
                        LOG.warning("User %s has lost all tcp connections. Trying to reconnect.", user.getNick());
                        userAdded(user);
                    } else if (clientCount > 1) {
                        LOG.warning("User %s has too many (%d) tcp connections. Trying to close.",
                                    user.getNick(), clientCount);
                        userClient.disconnectAdditionalClients();
                    }
                }

                reconnectUsersWithoutConnections();
            }

            catch (final RuntimeException e) {
                // Guard the monitor loop so a single unexpected exception does not permanently
                // kill TCP connection monitoring.
                LOG.warning("Unexpected error in TCP connection monitor, continuing: %s", e);
            }
        }
    }

    /**
     * Retries the tcp connection for online users that have no live connection.
     *
     * <p>The initial connection attempt in {@link #userAdded(User)} is never retried on
     * its own, so a single failed attempt (firewall, bad timing, ...) would otherwise
     * leave the user without tcp forever. Long messages are sent over tcp only, so a
     * missing connection means those messages are silently lost.</p>
     */
    private void reconnectUsersWithoutConnections() {
        final UserList userList = controller.getUserList();
        final List<User> usersToConnect = new ArrayList<>();

        synchronized (userList) {
            for (int i = 0; i < userList.size(); i++) {
                final User user = userList.get(i);

                if (!user.isMe() && user.isOnline()) {
                    usersToConnect.add(user);
                }
            }
        }

        for (final User user : usersToConnect) {
            final TCPUserClient userClient = userClients.get(user);

            if (userClient == null || userClient.getClientCount() == 0) {
                LOG.fine("User %s has no tcp connection. Trying to connect.", user.getNick());
                userAdded(user);
            }
        }
    }
}
