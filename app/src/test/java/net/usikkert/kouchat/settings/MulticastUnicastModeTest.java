
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

import org.junit.Test;

/**
 * Test of {@link MulticastUnicastMode}.
 *
 * @author Christian Ihle
 */
public class MulticastUnicastModeTest {

    @Test
    public void fromKeyShouldReturnMatchingMode() {
        assertEquals(MulticastUnicastMode.ALL, MulticastUnicastMode.fromKey("all"));
        assertEquals(MulticastUnicastMode.TRUSTED, MulticastUnicastMode.fromKey("trusted"));
        assertEquals(MulticastUnicastMode.OFF, MulticastUnicastMode.fromKey("off"));
    }

    @Test
    public void fromKeyShouldIgnoreCaseAndWhitespace() {
        assertEquals(MulticastUnicastMode.TRUSTED, MulticastUnicastMode.fromKey(" Trusted "));
        assertEquals(MulticastUnicastMode.OFF, MulticastUnicastMode.fromKey("OFF"));
    }

    @Test
    public void fromKeyShouldReturnAllOnNull() {
        assertEquals(MulticastUnicastMode.ALL, MulticastUnicastMode.fromKey(null));
    }

    @Test
    public void fromKeyShouldReturnAllOnUnknownKey() {
        assertEquals(MulticastUnicastMode.ALL, MulticastUnicastMode.fromKey("unknown"));
        assertEquals(MulticastUnicastMode.ALL, MulticastUnicastMode.fromKey(""));
    }
}
