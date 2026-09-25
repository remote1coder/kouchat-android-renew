
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Test of {@link ThemeMode}.
 *
 * @author Christian Ihle
 */
public class ThemeModeTest {

    @Test
    public void fromKeyShouldReturnMatchingMode() {
        assertEquals(ThemeMode.AUTO, ThemeMode.fromKey("auto"));
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey("system"));
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromKey("light"));
        assertEquals(ThemeMode.DARK, ThemeMode.fromKey("dark"));
    }

    @Test
    public void fromKeyShouldIgnoreCaseAndWhitespace() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey(" System "));
        assertEquals(ThemeMode.DARK, ThemeMode.fromKey("DARK"));
    }

    @Test
    public void fromKeyShouldReturnLightOnNull() {
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromKey(null));
    }

    @Test
    public void fromKeyShouldReturnLightOnUnknownKey() {
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromKey("unknown"));
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromKey(""));
    }

    @Test
    public void isDarkHourShouldHandleWindowWrappingAroundMidnight() {
        // Dark from 18:00 to 08:59
        assertFalse(ThemeMode.isDarkHour(17, 18, 9));
        assertTrue(ThemeMode.isDarkHour(18, 18, 9));
        assertTrue(ThemeMode.isDarkHour(23, 18, 9));
        assertTrue(ThemeMode.isDarkHour(0, 18, 9));
        assertTrue(ThemeMode.isDarkHour(8, 18, 9));
        assertFalse(ThemeMode.isDarkHour(9, 18, 9));
        assertFalse(ThemeMode.isDarkHour(12, 18, 9));
    }

    @Test
    public void isDarkHourShouldHandleWindowWithinSameDay() {
        // Dark from 09:00 to 17:59
        assertFalse(ThemeMode.isDarkHour(8, 9, 18));
        assertTrue(ThemeMode.isDarkHour(9, 9, 18));
        assertTrue(ThemeMode.isDarkHour(17, 9, 18));
        assertFalse(ThemeMode.isDarkHour(18, 9, 18));
        assertFalse(ThemeMode.isDarkHour(23, 9, 18));
    }

    @Test
    public void isDarkHourShouldNeverBeDarkWhenStartEqualsEnd() {
        for (int hour = 0; hour < 24; hour++) {
            assertFalse(ThemeMode.isDarkHour(hour, 9, 9));
        }
    }

    @Test
    public void isDarkHourShouldHandleAlmostFullDayWindow() {
        // Dark from 00:00 to 22:59
        for (int hour = 0; hour < 23; hour++) {
            assertTrue(ThemeMode.isDarkHour(hour, 0, 23));
        }

        assertFalse(ThemeMode.isDarkHour(23, 0, 23));
    }
}
