
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
 *   License along with KouChat.                                            *
 *   If not, see <http://www.gnu.org/licenses/>.                            *
 ***************************************************************************/

package net.usikkert.kouchat.settings;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Test of {@link NetworkMode}.
 *
 * @author Christian Ihle
 */
public class NetworkModeTest {

    @Test
    public void fromKeyShouldMapEachKeyToItsMode() {
        assertEquals(NetworkMode.MULTICAST, NetworkMode.fromKey("multicast"));
        assertEquals(NetworkMode.BROADCAST, NetworkMode.fromKey("lanbroadcast"));
        assertEquals(NetworkMode.UNICAST, NetworkMode.fromKey("unicast"));
        assertEquals(NetworkMode.P2P, NetworkMode.fromKey("p2p"));
    }

    @Test
    public void fromKeyShouldIgnoreCaseAndWhitespace() {
        assertEquals(NetworkMode.MULTICAST, NetworkMode.fromKey(" Multicast "));
        assertEquals(NetworkMode.P2P, NetworkMode.fromKey("P2P"));
    }

    @Test
    public void fromKeyShouldMapLegacyBroadcastKeyToMulticast() {
        // Older versions stored "broadcast" for what was actually multicast.
        assertEquals(NetworkMode.MULTICAST, NetworkMode.fromKey("broadcast"));
    }

    @Test
    public void fromKeyShouldDefaultToMulticastForUnknownOrNullKeys() {
        assertEquals(NetworkMode.MULTICAST, NetworkMode.fromKey(null));
        assertEquals(NetworkMode.MULTICAST, NetworkMode.fromKey(""));
        assertEquals(NetworkMode.MULTICAST, NetworkMode.fromKey("gibberish"));
    }

    @Test
    public void getKeyShouldReturnTheSettingsFileValue() {
        assertEquals("multicast", NetworkMode.MULTICAST.getKey());
        assertEquals("lanbroadcast", NetworkMode.BROADCAST.getKey());
        assertEquals("unicast", NetworkMode.UNICAST.getKey());
        assertEquals("p2p", NetworkMode.P2P.getKey());
    }
}
