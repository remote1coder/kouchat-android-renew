
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

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import net.usikkert.kouchat.Constants;
import net.usikkert.kouchat.event.SettingsListener;
import net.usikkert.kouchat.misc.User;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import net.usikkert.kouchat.junit.RestoreSystemProperties;

/**
 * Test of {@link Settings}.
 *
 * @author Christian Ihle
 */
@SuppressWarnings("HardCodedStringLiteral")
public class SettingsTest {

    @Rule
    public RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties();

    private Settings settings;

    private SettingsListener listener;
    private Setting lastChangedSetting;

    @Before
    public void setUp() throws Exception {
        settings = new Settings();

        System.setProperty("file.separator", "/");

        listener = new SettingsListener() {
            public void settingChanged(final Setting setting) {
                lastChangedSetting = setting;
            }
        };

        settings.addSettingsListener(listener);
    }

    @Test
    public void meShouldBeCreated() {
        final User me = settings.getMe();

        assertTrue(me.isMe());
    }

    @Test
    public void defaultValuesShouldBeSet() {
        verifyDefaultValues(settings);
    }

    @Test
    public void getLogLocationShouldReturnSetValue() {
        settings.setLogLocation("/var/log/kouchat/");

        assertEquals("/var/log/kouchat/", settings.getLogLocation());
    }

    @Test
    public void getLogLocationShouldAlwaysEndWithSlash() {
        settings.setLogLocation("/var/log/kouchat");

        assertEquals("/var/log/kouchat/", settings.getLogLocation());
    }

    @Test
    public void getLogLocationShouldReturnDefaultLocationOfValueNotSet() {
        settings.setLogLocation(null);

        assertEquals(Constants.APP_LOG_FOLDER, settings.getLogLocation());
    }

    @Test
    public void setLoggingShouldNotNotifyListenersIfSettingIsUnchanged() {
        assertFalse(settings.isLogging());

        settings.setLogging(false);

        assertFalse(settings.isLogging());
        assertNull(lastChangedSetting);
    }

    @Test
    public void setLoggingShouldNotifyListenersIfSettingIsChanged() {
        assertFalse(settings.isLogging());

        settings.setLogging(true);

        assertTrue(settings.isLogging());
        assertEquals(Setting.LOGGING, lastChangedSetting);
    }

    @Test
    public void isLoggingShouldBeTrueIfAlwaysLogIsEnabled() {
        assertFalse(settings.isLogging());

        settings.setAlwaysLog(true);

        assertTrue(settings.isLogging());
    }

    @Test
    public void setSystemTrayShouldNotNotifyListenersIfSettingIsUnchanged() {
        assertTrue(settings.isSystemTray());

        settings.setSystemTray(true);

        assertTrue(settings.isSystemTray());
        assertNull(lastChangedSetting);
    }

    @Test
    public void setSystemTrayShouldNotifyListenersIfSettingIsChanged() {
        assertTrue(settings.isSystemTray());

        settings.setSystemTray(false);

        assertFalse(settings.isSystemTray());
        assertEquals(Setting.SYSTEM_TRAY, lastChangedSetting);
    }

    @Test
    public void setClientShouldSetClientOnMeWithAppNameAndVersion() {
        final User me = settings.getMe();
        assertEquals("<unknown>", me.getClient());

        settings.setClient("SuperClient");

        assertEquals("KouChat v" + Constants.APP_VERSION + " SuperClient", me.getClient());
    }

    @Test
    public void fireSettingChangedShouldNotifyAllListeners() {
        settings.removeSettingsListener(listener);

        final SettingsListener listener1 = mock(SettingsListener.class);
        final SettingsListener listener2 = mock(SettingsListener.class);

        settings.addSettingsListener(listener1);
        settings.addSettingsListener(listener2);

        final Setting setting = new Setting("MONKEY");

        settings.fireSettingChanged(setting);

        verify(listener1).settingChanged(setting);
        verify(listener2).settingChanged(setting);
    }

    static void verifyDefaultValues(final Settings settings) {
        assertEquals(-15987646, settings.getOwnColor());
        assertEquals(-16759040, settings.getSysColor());

        assertTrue(settings.isSound());
        assertFalse(settings.isLogging());
        assertTrue(settings.isSmileys());
        assertFalse(settings.isBalloons());
        assertTrue(settings.isSystemTray());

        assertEquals("", settings.getBrowser());
        assertEquals("", settings.getLookAndFeel());
        assertNull(settings.getNetworkInterface());

        assertFalse(settings.isNoPrivateChat());
        assertFalse(settings.isAlwaysLog());
        assertEquals(Constants.APP_LOG_FOLDER, settings.getLogLocation());

        assertEquals(ThemeMode.LIGHT, settings.getThemeMode());
        assertEquals(18, settings.getThemeDarkStartHour());
        assertEquals(9, settings.getThemeDarkEndHour());
        assertEquals(AutoAcceptFiles.IMAGES, settings.getAutoAcceptFiles());

        assertEquals("", settings.getFontName());
        assertEquals(0, settings.getFontSize());

        assertEquals(MulticastUnicastMode.ALL, settings.getMulticastUnicastMode());
        assertTrue(settings.isAutoEncryptUnicast());
    }

    @Test
    public void setThemeModeShouldNotifyListenersIfSettingIsChanged() {
        assertEquals(ThemeMode.LIGHT, settings.getThemeMode());

        settings.setThemeMode(ThemeMode.DARK);

        assertEquals(ThemeMode.DARK, settings.getThemeMode());
        assertEquals(Setting.THEME, lastChangedSetting);
    }

    @Test
    public void setFontNameShouldNotifyListenersIfSettingIsChanged() {
        assertEquals("", settings.getFontName());

        settings.setFontName("Verdana");

        assertEquals("Verdana", settings.getFontName());
        assertEquals(Setting.FONT, lastChangedSetting);
    }

    @Test
    public void setFontNameShouldNotNotifyListenersIfSettingIsUnchanged() {
        settings.setFontName("");

        assertEquals("", settings.getFontName());
        assertNull(lastChangedSetting);
    }

    @Test
    public void setFontNameShouldHandleNullAsDefault() {
        settings.setFontName("Verdana");

        settings.setFontName(null);

        assertEquals("", settings.getFontName());
    }

    @Test
    public void setFontSizeShouldNotifyListenersIfSettingIsChanged() {
        assertEquals(0, settings.getFontSize());

        settings.setFontSize(16);

        assertEquals(16, settings.getFontSize());
        assertEquals(Setting.FONT, lastChangedSetting);
    }

    @Test
    public void setFontSizeShouldAllowZeroToResetToDefault() {
        settings.setFontSize(16);

        settings.setFontSize(0);

        assertEquals(0, settings.getFontSize());
    }

    @Test
    public void setFontSizeShouldIgnoreInvalidValues() {
        settings.setFontSize(7);
        assertEquals(0, settings.getFontSize());

        settings.setFontSize(65);
        assertEquals(0, settings.getFontSize());

        assertNull(lastChangedSetting);
    }

    @Test
    public void setThemeModeShouldNotNotifyListenersIfSettingIsUnchanged() {
        settings.setThemeMode(ThemeMode.LIGHT);

        assertEquals(ThemeMode.LIGHT, settings.getThemeMode());
        assertNull(lastChangedSetting);
    }

    @Test
    public void setThemeDarkHoursShouldNotifyListenersIfSettingIsChanged() {
        settings.setThemeDarkStartHour(20);
        settings.setThemeDarkEndHour(7);

        assertEquals(20, settings.getThemeDarkStartHour());
        assertEquals(7, settings.getThemeDarkEndHour());
        assertEquals(Setting.THEME, lastChangedSetting);
    }

    @Test
    public void setThemeDarkHoursShouldIgnoreValuesOutsideOfDay() {
        settings.setThemeDarkStartHour(24);
        settings.setThemeDarkStartHour(-1);
        settings.setThemeDarkEndHour(24);
        settings.setThemeDarkEndHour(-1);

        assertEquals(18, settings.getThemeDarkStartHour());
        assertEquals(9, settings.getThemeDarkEndHour());
        assertNull(lastChangedSetting);
    }

    @Test
    public void setAutoAcceptFilesShouldSetValue() {
        settings.setAutoAcceptFiles(AutoAcceptFiles.ALL);

        assertEquals(AutoAcceptFiles.ALL, settings.getAutoAcceptFiles());
    }
}
