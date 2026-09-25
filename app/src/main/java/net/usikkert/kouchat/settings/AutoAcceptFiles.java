
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
 * The scope of files that are automatically accepted from other users
 * when an end-to-end encrypted channel is active with that user.
 *
 * <ul>
 *   <li>{@link #OFF} - never automatically accept; always ask the user.</li>
 *   <li>{@link #IMAGES} - automatically accept image files only.</li>
 *   <li>{@link #ALL} - automatically accept any file.</li>
 * </ul>
 *
 * @author Christian Ihle
 */
public enum AutoAcceptFiles {

    /** Never automatically accept files; always ask the user. */
    OFF("off"),

    /** Automatically accept image files from encrypted users. */
    IMAGES("images"),

    /** Automatically accept any file from encrypted users. */
    ALL("all");

    private final String key;

    AutoAcceptFiles(final String key) {
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
     * Parses a stored key value back into a scope, defaulting to {@link #IMAGES}.
     *
     * @param key The stored key value, or <code>null</code>.
     * @return The matching scope, or {@link #IMAGES} if unknown.
     */
    public static AutoAcceptFiles fromKey(final String key) {
        if (key == null) {
            return IMAGES;
        }

        final String trimmedKey = key.trim();

        for (final AutoAcceptFiles scope : values()) {
            if (scope.key.equalsIgnoreCase(trimmedKey)) {
                return scope;
            }
        }

        return IMAGES;
    }
}
