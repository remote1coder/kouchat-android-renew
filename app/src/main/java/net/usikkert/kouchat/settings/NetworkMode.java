
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

package net.usikkert.kouchat.settings;

/**
 * The network mode the client operates in.
 *
 * <ul>
 *   <li>{@link #MULTICAST} - the default: main chat is sent over UDP multicast
 *       (+ TCP) and private chat over UDP unicast (+ TCP), as in original KouChat.
 *       Private chats still use end-to-end encryption once keys are trusted.</li>
 *   <li>{@link #BROADCAST} - like multicast, but main chat is sent over UDP subnet
 *       broadcast instead of the multicast group.</li>
 *   <li>{@link #UNICAST} - point-to-point unicast mesh without encryption: group
 *       chat is sent unencrypted to each online peer, but the discovery
 *       packets (logon, expose, idle, ...) are still multicast so new users are
 *       detected.</li>
 *   <li>{@link #P2P} - point-to-point encrypted mesh: the client establishes
 *       encrypted connections with other clients. Group chat is sent encrypted
 *       over unicast to each trusted peer (no multicast), but the discovery
 *       packets (logon, expose, idle, ...) are still multicast so new users are
 *       detected.</li>
 * </ul>
 *
 * @author Christian Ihle
 */
public enum NetworkMode {

    /** Multicast main chat, unicast private chat (original behavior). */
    MULTICAST("multicast"),

    /** Subnet broadcast main chat, unicast private chat. */
    BROADCAST("lanbroadcast"),

    /** Plain (unencrypted) unicast mesh; discovery packets still multicast. */
    UNICAST("unicast"),

    /** Encrypted unicast mesh; discovery packets still multicast. */
    P2P("p2p");

    private final String key;

    NetworkMode(final String key) {
        this.key = key;
    }

    /**
     * The string stored in the settings file.
     *
     * @return The settings key value.
     */
    public String getKey() {
        return key;
    }

    /**
     * Parses a stored key value back into a mode, defaulting to {@link #MULTICAST}.
     *
     * <p>The legacy value {@code "broadcast"} is mapped to {@link #MULTICAST}:
     * in older versions the mode named "broadcast" actually used multicast,
     * and existing users keep that behavior.</p>
     *
     * @param key The stored key value, or {@code null}.
     * @return The matching mode, or {@link #MULTICAST} if unknown.
     */
    public static NetworkMode fromKey(final String key) {
        if (key == null) {
            return MULTICAST;
        }

        final String trimmedKey = key.trim();

        // Legacy value from older versions, where "broadcast" meant multicast.
        if ("broadcast".equalsIgnoreCase(trimmedKey)) {
            return MULTICAST;
        }

        for (final NetworkMode mode : values()) {
            if (mode.key.equalsIgnoreCase(trimmedKey)) {
                return mode;
            }
        }

        return MULTICAST;
    }
}
