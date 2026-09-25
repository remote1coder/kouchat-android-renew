
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
 * The theme mode the client uses for the user interface.
 *
 * <ul>
 *   <li>{@link #AUTO} - switch between light and dark automatically, based on the
 *       time of day (configurable via {@link Settings#getThemeDarkStartHour()} and
 *       {@link Settings#getThemeDarkEndHour()}).</li>
 *   <li>{@link #SYSTEM} - follow the theme of the operating system.</li>
 *   <li>{@link #LIGHT} - always use the light theme.</li>
 *   <li>{@link #DARK} - always use the dark theme.</li>
 * </ul>
 *
 * @author Christian Ihle
 */
public enum ThemeMode {

    /** Automatically switch between light and dark theme, based on the time of day. */
    AUTO("auto"),

    /** Follow the theme of the operating system. */
    SYSTEM("system"),

    /** Always use the light theme. */
    LIGHT("light"),

    /** Always use the dark theme. */
    DARK("dark");

    private final String key;

    ThemeMode(final String key) {
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
     * Parses a stored key value back into a mode, defaulting to {@link #LIGHT}.
     *
     * @param key The stored key value, or <code>null</code>.
     * @return The matching mode, or {@link #LIGHT} if unknown.
     */
    public static ThemeMode fromKey(final String key) {
        if (key == null) {
            return LIGHT;
        }

        final String trimmedKey = key.trim();

        for (final ThemeMode mode : values()) {
            if (mode.key.equalsIgnoreCase(trimmedKey)) {
                return mode;
            }
        }

        return LIGHT;
    }

    /**
     * Checks if the given hour of the day falls within the dark theme time window.
     *
     * <p>The window wraps around midnight: with a start hour of 18 and an end hour of 9,
     * the dark theme is active from 18:00 to 08:59. If the start and end hours are equal,
     * the dark theme is never active.</p>
     *
     * @param currentHour The current hour of the day, 0-23.
     * @param darkStartHour The hour the dark theme starts, 0-23.
     * @param darkEndHour The hour the dark theme ends (light theme starts), 0-23.
     * @return True if the dark theme should be active at the given hour.
     */
    public static boolean isDarkHour(final int currentHour, final int darkStartHour, final int darkEndHour) {
        if (darkStartHour == darkEndHour) {
            return false;
        }

        // Wraps around midnight, e.g. 18 -> 9: dark from 18:00 to 08:59.
        if (darkStartHour > darkEndHour) {
            return currentHour >= darkStartHour || currentHour < darkEndHour;
        }

        // Same day, e.g. 9 -> 18: dark from 09:00 to 17:59.
        return currentHour >= darkStartHour && currentHour < darkEndHour;
    }
}
