
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
 * Which peers get an automatic redundant unicast channel in the multicast and
 * broadcast network modes. Some networks drop multicast or broadcast packets,
 * and a direct unicast channel to a peer keeps the chat working when that
 * happens. The unicast copy is sent in addition to the multicast copy, and
 * duplicates are removed on the receiving side.
 *
 * <ul>
 *   <li>{@link #ALL} - automatically establish a unicast channel with every
 *       discovered user (default).</li>
 *   <li>{@link #TRUSTED} - only with users whose encryption key has already
 *       been trusted in an earlier session.</li>
 *   <li>{@link #OFF} - never establish unicast channels automatically.</li>
 * </ul>
 *
 * @author Christian Ihle
 */
public enum MulticastUnicastMode {

    /** Automatically establish a unicast channel with every discovered user. */
    ALL("all"),

    /** Only establish a unicast channel with users whose key is already trusted. */
    TRUSTED("trusted"),

    /** Never establish unicast channels automatically. */
    OFF("off");

    private final String key;

    MulticastUnicastMode(final String key) {
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
     * Parses a stored key value back into a mode, defaulting to {@link #ALL}.
     *
     * @param key The stored key value, or <code>null</code>.
     * @return The matching mode, or {@link #ALL} if unknown.
     */
    public static MulticastUnicastMode fromKey(final String key) {
        if (key == null) {
            return ALL;
        }

        final String trimmedKey = key.trim();

        for (final MulticastUnicastMode mode : values()) {
            if (mode.key.equalsIgnoreCase(trimmedKey)) {
                return mode;
            }
        }

        return ALL;
    }
}
