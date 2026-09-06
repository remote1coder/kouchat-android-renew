
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

package net.usikkert.kouchat.crypto;

import static org.junit.Assert.*;

import javax.crypto.SecretKey;

import org.junit.Test;

/**
 * Test of {@link EncryptedChannel}.
 *
 * @author Christian Ihle
 */
public class EncryptedChannelTest {

    @Test
    public void encryptDecryptShouldRoundtrip() {
        final SecretKey key = CryptoUtils.generateAesKey();
        final EncryptedChannel channel = new EncryptedChannel(key);

        final String ciphertext = channel.encrypt("private message");
        assertEquals("private message", channel.decrypt(ciphertext));
    }

    @Test
    public void encryptShouldProduceDifferentCiphertextEachCall() {
        final SecretKey key = CryptoUtils.generateAesKey();
        final EncryptedChannel channel = new EncryptedChannel(key);

        assertNotEquals(channel.encrypt("same"), channel.encrypt("same"));
    }

    @Test
    public void decryptWithDifferentChannelKeyShouldFail() {
        final EncryptedChannel a = new EncryptedChannel(CryptoUtils.generateAesKey());
        final EncryptedChannel b = new EncryptedChannel(CryptoUtils.generateAesKey());

        final String ciphertext = a.encrypt("secret");

        try {
            b.decrypt(ciphertext);
            fail("Expected CryptoException");
        }

        catch (final CryptoException expected) {
            // wrong key -> GCM tag failure
        }
    }

    @Test
    public void messageCountShouldIncrement() {
        final EncryptedChannel channel = new EncryptedChannel(CryptoUtils.generateAesKey());

        assertEquals(0, channel.getMessageCount());
        channel.encrypt("one");
        channel.encrypt("two");
        assertEquals(2, channel.getMessageCount());
    }
}
