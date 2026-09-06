
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
 *   If not, see <http://www.gnu.org/licenses/lgpl-3.0.txt>.               *
 ***************************************************************************/

package net.usikkert.kouchat.android.settings;

import net.usikkert.kouchat.android.R;
import net.usikkert.kouchat.misc.User;
import net.usikkert.kouchat.settings.NetworkMode;
import net.usikkert.kouchat.util.Tools;
import net.usikkert.kouchat.util.Validate;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * Loads settings from the stored Android preferences.
 *
 * <p>Supports the following settings:</p>
 *
 * <ul>
 *   <li>Nick name</li>
 *   <li>Own color</li>
 *   <li>System color</li>
 *   <li>Wake lock</li>
 *   <li>Network interface</li>
 *   <li>Notification light</li>
 *   <li>Notification sound</li>
 *   <li>Notification vibration</li>
 * </ul>
 *
 * <p>Also sets the client, but not from any stored setting.</p>
 *
 * @author Christian Ihle
 */
public class AndroidSettingsLoader {

    private static final String DEFAULT_NICK_NAME = "NewUser";
    private static final String CLIENT = "Android";

    public void loadStoredSettings(final Context context, final AndroidSettings settings) {
        Validate.notNull(context, "Context can not be null");
        Validate.notNull(settings, "Settings can not be null");

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        loadClient(settings);

        loadNickName(context, preferences, settings.getMe());
        loadOwnColor(context, preferences, settings);
        loadSystemColor(context, preferences, settings);
        loadWakeLock(context, preferences, settings);
        loadNetworkInterface(context, preferences, settings);
        loadNetworkMode(context, preferences, settings);

        loadNotificationLight(context, preferences, settings);
        loadNotificationSound(context, preferences, settings);
        loadNotificationVibration(context, preferences, settings);
    }

    private void loadClient(final AndroidSettings settings) {
        settings.setClient(CLIENT);
    }

    private void loadNickName(final Context context, final SharedPreferences preferences, final User me) {
        final String nickNameKey = context.getString(R.string.settings_nick_name_key);
        final String nickName = getNickNameFromPreferences(preferences, nickNameKey);

        me.setNick(nickName);
    }

    private String getNickNameFromPreferences(final SharedPreferences preferences, final String nickNameKey) {
        final String nickNameFromPreferences = preferences.getString(nickNameKey, null);

        if (Tools.isValidNick(nickNameFromPreferences)) {
            return nickNameFromPreferences;
        }

        return DEFAULT_NICK_NAME;
    }

    private void loadOwnColor(final Context context, final SharedPreferences preferences,
                              final AndroidSettings settings) {
        final String ownColorKey = context.getString(R.string.settings_own_color_key);
        final int ownColor = preferences.getInt(ownColorKey, -1);

        if (ownColor != -1) {
            settings.setOwnColor(ownColor);
        }
    }

    private void loadSystemColor(final Context context, final SharedPreferences preferences,
                                 final AndroidSettings settings) {
        final String systemColorKey = context.getString(R.string.settings_sys_color_key);
        final int systemColor = preferences.getInt(systemColorKey, -1);

        if (systemColor != -1) {
            settings.setSysColor(systemColor);
        }
    }

    private void loadWakeLock(final Context context, final SharedPreferences preferences,
                              final AndroidSettings settings) {
        final String wakeLockKey = context.getString(R.string.settings_wake_lock_key);
        final boolean wakeLockEnabled = preferences.getBoolean(wakeLockKey, false);

        settings.setWakeLockEnabled(wakeLockEnabled);
    }

    private void loadNetworkInterface(final Context context, final SharedPreferences preferences,
                                      final AndroidSettings settings) {
        final String preferenceKey = context.getString(R.string.settings_network_interface_key);
        final String networkInterface = preferences.getString(preferenceKey, "");

        settings.setNetworkInterface(networkInterface != null && !networkInterface.isEmpty() ? networkInterface : null);
    }

    private void loadNetworkMode(final Context context, final SharedPreferences preferences,
                                 final AndroidSettings settings) {
        final String preferenceKey = context.getString(R.string.settings_network_mode_key);
        final String value = preferences.getString(preferenceKey, "multicast");
        // Older versions stored "broadcast" for what was actually multicast; fromKey handles that.
        settings.setNetworkMode(NetworkMode.fromKey(value));
    }

    private void loadNotificationLight(final Context context, final SharedPreferences preferences,
                                       final AndroidSettings settings) {
        final String preferenceKey = context.getString(R.string.settings_notification_light_key);
        final boolean preferenceEnabled = preferences.getBoolean(preferenceKey, true);

        settings.setNotificationLightEnabled(preferenceEnabled);
    }

    private void loadNotificationSound(final Context context, final SharedPreferences preferences,
                                       final AndroidSettings settings) {
        final String preferenceKey = context.getString(R.string.settings_notification_sound_key);
        final boolean preferenceEnabled = preferences.getBoolean(preferenceKey, true);

        settings.setNotificationSoundEnabled(preferenceEnabled);
    }

    private void loadNotificationVibration(final Context context, final SharedPreferences preferences,
                                           final AndroidSettings settings) {
        final String preferenceKey = context.getString(R.string.settings_notification_vibration_key);
        final boolean preferenceEnabled = preferences.getBoolean(preferenceKey, true);

        settings.setNotificationVibrationEnabled(preferenceEnabled);
    }
}
