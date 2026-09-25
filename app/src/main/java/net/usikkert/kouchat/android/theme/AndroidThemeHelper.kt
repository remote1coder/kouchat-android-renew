
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

package net.usikkert.kouchat.android.theme

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import net.usikkert.kouchat.settings.ThemeMode

/**
 * Applies the theme mode (light, dark, automatic by time of day, or follow
 * system) chosen in the settings, using [AppCompatDelegate.setDefaultNightMode].
 *
 * Should be called before any activity is created, and again when the app
 * is resumed, so the automatic mode follows the time of day.
 *
 * @author Christian Ihle
 */
object AndroidThemeHelper {

    private const val THEME_MODE_KEY = "theme_mode"
    private const val DARK_START_HOUR_KEY = "dark_start_hour"
    private const val DARK_END_HOUR_KEY = "dark_end_hour"

    /**
     * Reads the theme settings from the default shared preferences, and
     * applies the corresponding night mode, but only if it differs from
     * the currently applied mode.
     *
     * @param context The context to read the settings from.
     */
    @JvmStatic
    fun applyTheme(context: Context) {
        val preferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)

        val themeMode = ThemeMode.fromKey(preferences.getString(THEME_MODE_KEY, null))
        val darkStartHour = getHour(preferences.getString(DARK_START_HOUR_KEY, null), 18)
        val darkEndHour = getHour(preferences.getString(DARK_END_HOUR_KEY, null), 9)

        val wantedMode = when (themeMode) {
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemeMode.AUTO ->
                if (ThemeMode.isDarkHour(currentHour(), darkStartHour, darkEndHour)) {
                    AppCompatDelegate.MODE_NIGHT_YES
                } else {
                    AppCompatDelegate.MODE_NIGHT_NO
                }
        }

        // Only apply when it differs, to avoid recreating activities needlessly
        if (AppCompatDelegate.getDefaultNightMode() != wantedMode) {
            AppCompatDelegate.setDefaultNightMode(wantedMode)
        }
    }

    /**
     * Parses an hour of the day from a stored preference value.
     *
     * @param value The stored value.
     * @param defaultHour The default hour, if the value is missing or invalid.
     * @return The hour of the day, 0-23.
     */
    private fun getHour(value: String?, defaultHour: Int): Int {
        if (value == null) {
            return defaultHour
        }

        return try {
            val hour = Integer.parseInt(value)

            if (hour in 0..23) hour else defaultHour
        } catch (e: NumberFormatException) {
            defaultHour
        }
    }

    /**
     * The current hour of the day.
     *
     * @return The current hour, 0-23.
     */
    private fun currentHour(): Int {
        return java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    }
}
