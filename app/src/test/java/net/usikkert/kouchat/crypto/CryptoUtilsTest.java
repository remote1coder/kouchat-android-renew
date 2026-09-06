
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

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

import javax.crypto.SecretKey;

import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Test of {@link CryptoUtils}.
 *
 * @author Christian Ihle
 */
public class CryptoUtilsTest {

    private static KeyPair keyPair;

    @BeforeClass
    public static void generateKeyPair() {
        // RSA-4096 generation is ~1s, so do it once for the whole class.
        keyPair = CryptoUtils.generateRsaKeyPair();
    }

    @Test
    public void generateRsaKeyPairShouldProduce4096BitKey() {
        final KeyPair kp = CryptoUtils.generateRsaKeyPair();
        assertNotNull(kp.getPublic());
        assertNotNull(kp.getPrivate());
        // X.509 encoding of an RSA-4096 public key is ~550 bytes.
        assertTrue("RSA key should be 4096 bits",
                   kp.getPublic().getEncoded().length > 500);
    }

    @Test
    public void generateAesKeyShouldProduce256BitKey() {
        final SecretKey key = CryptoUtils.generateAesKey();
        assertEquals("AES", key.getAlgorithm());
        assertEquals(32, key.getEncoded().length);
    }

    @Test
    public void aesGcmShouldRoundtrip() {
        final SecretKey key = CryptoUtils.generateAesKey();
        final String plaintext = "Hello, encrypted world! 你好"; // include non-ASCII

        final String ciphertext = CryptoUtils.encryptAesGcm(key, plaintext);
        assertNotEquals(plaintext, ciphertext);

        final String decrypted = CryptoUtils.decryptAesGcm(key, ciphertext);
        assertEquals(plaintext, decrypted);
    }

    @Test
    public void aesGcmWithWrongKeyShouldFail() {
        final SecretKey key1 = CryptoUtils.generateAesKey();
        final SecretKey key2 = CryptoUtils.generateAesKey();
        final String ciphertext = CryptoUtils.encryptAesGcm(key1, "secret");

        try {
            CryptoUtils.decryptAesGcm(key2, ciphertext);
            fail("Expected CryptoException on decryption with wrong key");
        }

        catch (final CryptoException expected) {
            // GCM tag verification fails -> AEADBadTag.
        }
    }

    @Test
    public void aesGcmCiphertextShouldBeBase64() {
        final SecretKey key = CryptoUtils.generateAesKey();
        final String ciphertext = CryptoUtils.encryptAesGcm(key, "msg");

        // Must be valid base64 (no newlines, decodes cleanly).
        Base64.getDecoder().decode(ciphertext);
    }

    @Test
    public void twoEncryptionsShouldProduceDifferentCiphertext() {
        // Random IV per message means same plaintext encrypts differently.
        final SecretKey key = CryptoUtils.generateAesKey();
        final String c1 = CryptoUtils.encryptAesGcm(key, "same");
        final String c2 = CryptoUtils.encryptAesGcm(key, "same");
        assertNotEquals(c1, c2);
    }

    @Test
    public void wrapAndUnwrapSessionKeyShouldRoundtrip() {
        final SecretKey sessionKey = CryptoUtils.generateAesKey();
        final PublicKey pub = keyPair.getPublic();
        final PrivateKey priv = keyPair.getPrivate();

        final String wrapped = CryptoUtils.wrapSessionKey(pub, sessionKey);
        final SecretKey unwrapped = CryptoUtils.unwrapSessionKey(priv, wrapped);

        assertArrayEquals(sessionKey.getEncoded(), unwrapped.getEncoded());
    }

    @Test
    public void signAndVerifyShouldAuthenticate() {
        final PrivateKey priv = keyPair.getPrivate();
        final PublicKey pub = keyPair.getPublic();
        final byte[] data = "payload to sign".getBytes();

        final String signature = CryptoUtils.sign(priv, data);
        assertTrue(CryptoUtils.verify(pub, data, signature));
    }

    @Test
    public void verifyShouldFailForTamperedData() {
        final PrivateKey priv = keyPair.getPrivate();
        final PublicKey pub = keyPair.getPublic();
        final byte[] data = "original".getBytes();

        final String signature = CryptoUtils.sign(priv, data);
        assertFalse(CryptoUtils.verify(pub, "tampered".getBytes(), signature));
    }

    @Test
    public void fingerprintShouldBeStableAndHex() {
        final PublicKey pub = keyPair.getPublic();
        final String fp1 = CryptoUtils.fingerprint(pub);
        final String fp2 = CryptoUtils.fingerprint(pub);

        assertEquals(fp1, fp2);
        assertTrue("fingerprint should be hex pairs separated by colons",
                   fp1.matches("([0-9A-F]{2}:)+[0-9A-F]{2}"));
    }

    @Test
    public void fingerprintShouldDifferForDifferentKeys() {
        final KeyPair other = CryptoUtils.generateRsaKeyPair();
        assertNotEquals(CryptoUtils.fingerprint(keyPair.getPublic()),
                        CryptoUtils.fingerprint(other.getPublic()));
    }

    @Test
    public void groupFingerprintShouldGroupInFours() {
        final String raw = "AB:CD:EF:01";
        final String grouped = CryptoUtils.groupFingerprint(raw);
        assertEquals("ABCD EF01", grouped);
    }

    @Test
    public void base64ShouldRoundtrip() {
        final byte[] data = { 0, 1, 2, 3, (byte) 0xFF };
        final String b64 = CryptoUtils.base64Encode(data);
        assertArrayEquals(data, CryptoUtils.base64Decode(b64));
    }
}
