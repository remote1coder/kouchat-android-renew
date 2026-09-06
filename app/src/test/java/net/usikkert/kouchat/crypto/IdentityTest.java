
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

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.After;
import org.junit.Test;

/**
 * Test of {@link Identity}.
 *
 * @author Christian Ihle
 */
public class IdentityTest {

    private Path privFile;
    private Path pubFile;

    private Identity newIdentity() throws Exception {
        privFile = Files.createTempFile("kouchat-identity-priv", ".key");
        pubFile = Files.createTempFile("kouchat-identity-pub", ".key");
        Files.delete(privFile);
        Files.delete(pubFile);
        return new Identity(privFile.toString(), pubFile.toString());
    }

    @After
    public void tearDown() throws Exception {
        if (privFile != null) {
            Files.deleteIfExists(privFile);
        }

        if (pubFile != null) {
            Files.deleteIfExists(pubFile);
        }
    }

    @Test
    public void shouldGenerateAndPersistKeyFiles() throws Exception {
        final Identity identity = newIdentity();

        assertTrue(Files.exists(privFile));
        assertTrue(Files.exists(pubFile));
        assertNotNull(identity.getPublicKey());
        assertNotNull(identity.getPrivateKey());
    }

    @Test
    public void fingerprintShouldBeStableAcrossReload() throws Exception {
        final Identity first = newIdentity();
        final String fingerprint = first.getFingerprint();

        // Reload from the same files - should be the same identity.
        final Identity reloaded = new Identity(privFile.toString(), pubFile.toString());
        assertEquals(fingerprint, reloaded.getFingerprint());
    }

    @Test
    public void publicKeyBase64ShouldRoundtrip() throws Exception {
        final Identity identity = newIdentity();
        final String b64 = identity.getPublicKeyBase64();

        assertEquals(identity.getPublicKey(),
                     Identity.publicKeyFromBase64(b64));
    }

    @Test
    public void differentIdentitiesShouldHaveDifferentFingerprints() throws Exception {
        final Identity a = newIdentity();
        // Clean up so newIdentity() below uses fresh files.
        Files.deleteIfExists(privFile);
        Files.deleteIfExists(pubFile);

        final Path priv2 = Files.createTempFile("kouchat-identity-priv-2", ".key");
        final Path pub2 = Files.createTempFile("kouchat-identity-pub-2", ".key");
        Files.delete(priv2);
        Files.delete(pub2);

        try {
            final Identity b = new Identity(priv2.toString(), pub2.toString());
            assertNotEquals(a.getFingerprint(), b.getFingerprint());
        }

        finally {
            Files.deleteIfExists(priv2);
            Files.deleteIfExists(pub2);
        }
    }
}
