
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

package net.usikkert.kouchat.android.controller;

import net.usikkert.kouchat.android.R;
import net.usikkert.kouchat.android.chatwindow.AndroidUserInterface;
import net.usikkert.kouchat.android.component.HoloColorPickerPreference;
import net.usikkert.kouchat.android.component.HoloColorPickerPreferenceDialog;
import net.usikkert.kouchat.android.service.ChatService;
import net.usikkert.kouchat.android.service.ChatServiceBinder;
import net.usikkert.kouchat.android.settings.AndroidSettings;
import net.usikkert.kouchat.android.theme.AndroidThemeHelper;
import net.usikkert.kouchat.net.NetworkInterfaceInfo;
import net.usikkert.kouchat.net.NetworkUtils;
import net.usikkert.kouchat.settings.AutoAcceptFiles;
import net.usikkert.kouchat.settings.MulticastUnicastMode;
import net.usikkert.kouchat.settings.NetworkMode;
import net.usikkert.kouchat.settings.ThemeMode;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import androidx.fragment.app.DialogFragment;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.TwoStatePreference;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment for changing the settings.
 *
 * @author Christian Ihle
 */
public class SettingsFragment extends PreferenceFragmentCompat
                              implements Preference.OnPreferenceChangeListener,
                                         SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String DIALOG_FRAGMENT_TAG = "SettingsFragment.DIALOG";

    private AndroidUserInterface androidUserInterface;
    private AndroidSettings settings;

    private ServiceConnection serviceConnection;

    private String nickNameKey;
    private String wakeLockKey;
    private String ownColorKey;
    private String systemColorKey;
    private String networkInterfaceKey;
    private String networkModeKey;
    private String themeModeKey;
    private String darkStartHourKey;
    private String darkEndHourKey;
    private String autoAcceptFilesKey;
    private String multicastUnicastKey;
    private String autoEncryptUnicastKey;

    private String notificationLightKey;
    private String notificationSoundKey;
    private String notificationVibrationKey;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        serviceConnection = createServiceConnection();
        final Intent chatServiceIntent = createChatServiceIntent();
        getActivity().bindService(chatServiceIntent, serviceConnection, Context.BIND_NOT_FOREGROUND);
    }

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResource(R.xml.settings);

        nickNameKey = getString(R.string.settings_nick_name_key);
        wakeLockKey = getString(R.string.settings_wake_lock_key);
        ownColorKey = getString(R.string.settings_own_color_key);
        systemColorKey = getString(R.string.settings_sys_color_key);
        networkInterfaceKey = getString(R.string.settings_network_interface_key);
        networkModeKey = getString(R.string.settings_network_mode_key);
        themeModeKey = getString(R.string.settings_theme_mode_key);
        darkStartHourKey = getString(R.string.settings_dark_start_hour_key);
        darkEndHourKey = getString(R.string.settings_dark_end_hour_key);
        autoAcceptFilesKey = getString(R.string.settings_auto_accept_files_key);
        multicastUnicastKey = getString(R.string.settings_multicast_unicast_key);
        autoEncryptUnicastKey = getString(R.string.settings_auto_encrypt_unicast_key);

        notificationLightKey = getString(R.string.settings_notification_light_key);
        notificationSoundKey = getString(R.string.settings_notification_sound_key);
        notificationVibrationKey = getString(R.string.settings_notification_vibration_key);

        final Preference nickNamePreference = findPreference(nickNameKey);
        nickNamePreference.setOnPreferenceChangeListener(this);
        setValueAsSummary(nickNamePreference);

        final ListPreference networkInterfacePreference = findPreference(networkInterfaceKey);

        if (networkInterfacePreference != null) {
            populateNetworkInterfaces(networkInterfacePreference);
            updateNetworkInterfaceSummary(networkInterfacePreference);
        }

        final ListPreference networkModePreference = findPreference(networkModeKey);

        if (networkModePreference != null) {
            updateNetworkModeSummary(networkModePreference);
        }

        final ListPreference themeModePreference = findPreference(themeModeKey);

        if (themeModePreference != null) {
            updateThemeModeSummary(themeModePreference);
        }

        final ListPreference darkStartHourPreference = findPreference(darkStartHourKey);

        if (darkStartHourPreference != null) {
            updateHourSummary(darkStartHourPreference);
        }

        final ListPreference darkEndHourPreference = findPreference(darkEndHourKey);

        if (darkEndHourPreference != null) {
            updateHourSummary(darkEndHourPreference);
        }

        final ListPreference autoAcceptFilesPreference = findPreference(autoAcceptFilesKey);

        if (autoAcceptFilesPreference != null) {
            updateAutoAcceptFilesSummary(autoAcceptFilesPreference);
        }

        final ListPreference multicastUnicastPreference = findPreference(multicastUnicastKey);

        if (multicastUnicastPreference != null) {
            updateMulticastUnicastSummary(multicastUnicastPreference);
        }
    }

    /**
     * Handles validation when the nick name is about to be changed, and changes the actual nick name if it's valid.
     *
     * {@inheritDoc}
     */
    @Override
    public boolean onPreferenceChange(final Preference preference, final Object value) {
        if (preference.getKey().equals(nickNameKey)) {
            return androidUserInterface.changeNickName(value.toString());
        }

        return true;
    }

    /**
     * Updates state after a setting has been changed and saved.
     *
     * <ul>
     *   <li>Changed nick name: the nick name is set as the summary of the preference.</li>
     *   <li>Changed wake lock: stores the setting in the {@link AndroidSettings}.</li>
     *   <li>Changed own color: stores the setting in the {@link AndroidSettings}.</li>
     *   <li>Changed system color: stores the setting in the {@link AndroidSettings}.</li>
     *   <li>Changed network interface: stores the setting in the {@link AndroidSettings}.</li>
     *   <li>Changed notification light: stores the setting in the {@link AndroidSettings}.</li>
     *   <li>Changed notification sound: stores the setting in the {@link AndroidSettings}.</li>
     *   <li>Changed notification vibration: stores the setting in the {@link AndroidSettings}.</li>
     * </ul>
     *
     * {@inheritDoc}
     */
    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key) {
        if (key.equals(nickNameKey)) {
            setValueAsSummary(key);
        }

        else if (key.equals(wakeLockKey)) {
            final TwoStatePreference preference = (TwoStatePreference) findPreference(key);
            settings.setWakeLockEnabled(preference.isChecked());
        }

        else if (key.equals(ownColorKey)) {
            final HoloColorPickerPreference preference = (HoloColorPickerPreference) findPreference(key);
            settings.setOwnColor(preference.getPersistedColor());
        }

        else if (key.equals(systemColorKey)) {
            final HoloColorPickerPreference preference = (HoloColorPickerPreference) findPreference(key);
            settings.setSysColor(preference.getPersistedColor());
        }

        else if (key.equals(networkInterfaceKey)) {
            final ListPreference preference = (ListPreference) findPreference(key);
            updateNetworkInterfaceSummary(preference);

            if (settings != null) {
                final String value = preference.getValue();
                settings.setNetworkInterface(value != null && !value.isEmpty() ? value : null);
            }
        }

        else if (key.equals(networkModeKey)) {
            final ListPreference preference = (ListPreference) findPreference(key);
            updateNetworkModeSummary(preference);

            if (settings != null && androidUserInterface != null) {
                final NetworkMode mode = NetworkMode.fromKey(preference.getValue());
                settings.setNetworkMode(mode);
                androidUserInterface.setNetworkMode(mode);
            }
        }

        else if (key.equals(themeModeKey)) {
            final ListPreference preference = (ListPreference) findPreference(key);
            updateThemeModeSummary(preference);

            if (settings != null) {
                settings.setThemeMode(ThemeMode.fromKey(preference.getValue()));
            }

            if (getActivity() != null) {
                AndroidThemeHelper.applyTheme(getActivity());
            }
        }

        else if (key.equals(darkStartHourKey)) {
            final ListPreference preference = (ListPreference) findPreference(key);
            updateHourSummary(preference);

            if (settings != null) {
                settings.setThemeDarkStartHour(Integer.parseInt(preference.getValue()));
            }
        }

        else if (key.equals(darkEndHourKey)) {
            final ListPreference preference = (ListPreference) findPreference(key);
            updateHourSummary(preference);

            if (settings != null) {
                settings.setThemeDarkEndHour(Integer.parseInt(preference.getValue()));
            }
        }

        else if (key.equals(autoAcceptFilesKey)) {
            final ListPreference preference = (ListPreference) findPreference(key);
            updateAutoAcceptFilesSummary(preference);

            if (settings != null) {
                settings.setAutoAcceptFiles(AutoAcceptFiles.fromKey(preference.getValue()));
            }
        }

        else if (key.equals(multicastUnicastKey)) {
            final ListPreference preference = (ListPreference) findPreference(key);
            updateMulticastUnicastSummary(preference);

            if (settings != null && androidUserInterface != null) {
                settings.setMulticastUnicastMode(MulticastUnicastMode.fromKey(preference.getValue()));
                androidUserInterface.applyMulticastUnicastSettings();
            }
        }

        else if (key.equals(autoEncryptUnicastKey)) {
            final TwoStatePreference preference = (TwoStatePreference) findPreference(key);

            if (settings != null && androidUserInterface != null) {
                settings.setAutoEncryptUnicast(preference.isChecked());
                androidUserInterface.applyMulticastUnicastSettings();
            }
        }

        else if (key.equals(notificationLightKey)) {
            final TwoStatePreference preference = (TwoStatePreference) findPreference(key);
            settings.setNotificationLightEnabled(preference.isChecked());
        }

        else if (key.equals(notificationSoundKey)) {
            final TwoStatePreference preference = (TwoStatePreference) findPreference(key);
            settings.setNotificationSoundEnabled(preference.isChecked());
        }

        else if (key.equals(notificationVibrationKey)) {
            final TwoStatePreference preference = (TwoStatePreference) findPreference(key);
            settings.setNotificationVibrationEnabled(preference.isChecked());
        }
    }

    /**
     * Called when a preference in the tree requests to display a dialog.
     *
     * <p>See super for details.</p>
     *
     * @param preference The Preference object requesting the dialog.
     */
    @Override
    public void onDisplayPreferenceDialog(final Preference preference) {
        // Check if dialog is already showing
        if (getFragmentManager().findFragmentByTag(DIALOG_FRAGMENT_TAG) != null) {
            return;
        }

        if (preference instanceof HoloColorPickerPreference) {
            final DialogFragment dialog = HoloColorPickerPreferenceDialog.newInstance(preference.getKey());
            dialog.setTargetFragment(this, 0);
            dialog.show(getFragmentManager(), DIALOG_FRAGMENT_TAG);
        }

        // Give control to super so it can display the default dialog types when necessary
        else {
            super.onDisplayPreferenceDialog(preference);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onDestroy() {
        if (androidUserInterface != null) {
            getActivity().unbindService(serviceConnection);
        }

        androidUserInterface = null;
        settings = null;
        serviceConnection = null;

        super.onDestroy();
    }

    private void setValueAsSummary(final String key) {
        final Preference preference = findPreference(key);
        setValueAsSummary(preference);
    }

    /**
     * Sets the current value of a setting as the summary, so it's visible without clicking
     * on the setting to change it. Unless it's not set, in which case the default summary is left untouched.
     *
     * @param preference The setting to update.
     */
    private void setValueAsSummary(final Preference preference) {
        final EditTextPreference editTextPreference = (EditTextPreference) preference;

        if (editTextPreference.getText() != null) {
            preference.setSummary(editTextPreference.getText());
        }
    }

    /**
     * Populates the network interface preference with the usable network interfaces,
     * including an Auto option for automatic detection.
     *
     * @param preference The network interface preference.
     */
    private void populateNetworkInterfaces(final ListPreference preference) {
        try {
            final NetworkUtils networkUtils = new NetworkUtils();
            final List<NetworkInterfaceInfo> usableNetworkInterfaces = networkUtils.getUsableNetworkInterfaces();

            final List<String> entries = new ArrayList<>();
            final List<String> entryValues = new ArrayList<>();

            entries.add(getString(R.string.settings_network_interface_auto));
            entryValues.add("");

            for (final NetworkInterfaceInfo usableNetworkInterface : usableNetworkInterfaces) {
                entries.add(usableNetworkInterface.getName() + " - " + networkUtils.getIPv4Addresses(usableNetworkInterface));
                entryValues.add(usableNetworkInterface.getName());
            }

            preference.setEntries(entries.toArray(new String[0]));
            preference.setEntryValues(entryValues.toArray(new String[0]));
        }

        catch (final Exception e) {
            android.util.Log.e("KouChat", "Could not populate network interfaces", e);
        }
    }

    /**
     * Updates the summary of the network interface preference to show the current selection.
     *
     * @param preference The network interface preference.
     */
    private void updateNetworkInterfaceSummary(final ListPreference preference) {
        final CharSequence entry = preference.getEntry();

        if (entry != null) {
            preference.setSummary(entry);
        }

        else {
            preference.setSummary(getString(R.string.settings_network_interface_auto));
        }
    }

    /**
     * Updates the summary of the network mode preference to show the current selection.
     *
     * <p>Values stored by older versions (like the legacy "broadcast", which meant
     * multicast) have no matching entry in the list, so the label is derived from
     * the parsed {@link NetworkMode} instead.</p>
     *
     * @param preference The network mode preference.
     */
    private void updateNetworkModeSummary(final ListPreference preference) {
        CharSequence entry = preference.getEntry();

        if (entry == null) {
            entry = getNetworkModeLabel(NetworkMode.fromKey(preference.getValue()));
        }

        preference.setSummary(entry != null ? entry : getString(R.string.settings_network_mode_multicast));
    }

    /**
     * Finds the localized label of a network mode.
     *
     * @param networkMode The network mode.
     * @return The localized label.
     */
    private CharSequence getNetworkModeLabel(final NetworkMode networkMode) {
        switch (networkMode) {
            case P2P:
                return getString(R.string.settings_network_mode_p2p);

            case UNICAST:
                return getString(R.string.settings_network_mode_unicast);

            case BROADCAST:
                return getString(R.string.settings_network_mode_broadcast);

            default:
                return getString(R.string.settings_network_mode_multicast);
        }
    }

    /**
     * Updates the summary of the theme mode preference to show the current selection.
     *
     * @param preference The theme mode preference.
     */
    private void updateThemeModeSummary(final ListPreference preference) {
        CharSequence entry = preference.getEntry();

        if (entry == null) {
            entry = getThemeModeLabel(ThemeMode.fromKey(preference.getValue()));
        }

        preference.setSummary(entry != null ? entry : getString(R.string.settings_theme_mode_light));
    }

    /**
     * Finds the localized label of a theme mode.
     *
     * @param themeMode The theme mode.
     * @return The localized label.
     */
    private CharSequence getThemeModeLabel(final ThemeMode themeMode) {
        switch (themeMode) {
            case AUTO:
                return getString(R.string.settings_theme_mode_auto);

            case SYSTEM:
                return getString(R.string.settings_theme_mode_system);

            case DARK:
                return getString(R.string.settings_theme_mode_dark);

            default:
                return getString(R.string.settings_theme_mode_light);
        }
    }

    /**
     * Updates the summary of a dark theme hour preference to show the current selection.
     *
     * @param preference The hour preference.
     */
    private void updateHourSummary(final ListPreference preference) {
        final CharSequence entry = preference.getEntry();

        if (entry != null) {
            preference.setSummary(entry);
        }
    }

    /**
     * Updates the summary of the auto-accept files preference to show the current selection.
     *
     * @param preference The auto-accept files preference.
     */
    private void updateAutoAcceptFilesSummary(final ListPreference preference) {
        CharSequence entry = preference.getEntry();

        if (entry == null) {
            entry = getAutoAcceptFilesLabel(AutoAcceptFiles.fromKey(preference.getValue()));
        }

        preference.setSummary(entry != null ? entry : getString(R.string.settings_auto_accept_images));
    }

    /**
     * Finds the localized label of an auto-accept files scope.
     *
     * @param autoAcceptFiles The auto-accept files scope.
     * @return The localized label.
     */
    private CharSequence getAutoAcceptFilesLabel(final AutoAcceptFiles autoAcceptFiles) {
        switch (autoAcceptFiles) {
            case OFF:
                return getString(R.string.settings_auto_accept_off);

            case ALL:
                return getString(R.string.settings_auto_accept_all);

            default:
                return getString(R.string.settings_auto_accept_images);
        }
    }

    /**
     * Updates the summary of the multicast unicast preference to show the current selection.
     *
     * @param preference The multicast unicast preference.
     */
    private void updateMulticastUnicastSummary(final ListPreference preference) {
        CharSequence entry = preference.getEntry();

        if (entry == null) {
            entry = getMulticastUnicastLabel(MulticastUnicastMode.fromKey(preference.getValue()));
        }

        preference.setSummary(entry != null ? entry : getString(R.string.settings_multicast_unicast_all));
    }

    /**
     * Finds the localized label of a multicast unicast mode.
     *
     * @param multicastUnicastMode The multicast unicast mode.
     * @return The localized label.
     */
    private CharSequence getMulticastUnicastLabel(final MulticastUnicastMode multicastUnicastMode) {
        switch (multicastUnicastMode) {
            case TRUSTED:
                return getString(R.string.settings_multicast_unicast_trusted);

            case OFF:
                return getString(R.string.settings_multicast_unicast_off);

            default:
                return getString(R.string.settings_multicast_unicast_all);
        }
    }

    private Intent createChatServiceIntent() {
        return new Intent(getActivity(), ChatService.class);
    }

    private ServiceConnection createServiceConnection() {
        return new ServiceConnection() {
            @Override
            public void onServiceConnected(final ComponentName componentName, final IBinder iBinder) {
                final ChatServiceBinder binder = (ChatServiceBinder) iBinder;
                androidUserInterface = binder.getAndroidUserInterface();
                settings = androidUserInterface.getSettings();
            }

            @Override
            public void onServiceDisconnected(final ComponentName componentName) { }
        };
    }
}
