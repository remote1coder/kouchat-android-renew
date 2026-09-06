
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
import org.junit.Before;
import org.junit.Test;

/**
 * Test of {@link TrustedKeys}.
 *
 * @author Christian Ihle
 */
public class TrustedKeysTest {

    private Path tempFile;
    private TrustedKeys trustedKeys;

    @Before
    public void setUp() throws Exception {
        tempFile = Files.createTempFile("kouchat-trusted-keys-test", ".ini");
        Files.delete(tempFile); // TrustedKeys should handle a missing file.
        trustedKeys = new TrustedKeys(tempFile.toString());
        trustedKeys.load();
    }

    @After
    public void tearDown() throws Exception {
        Files.deleteIfExists(tempFile);
    }

    @Test
    public void emptyStoreShouldTrustNothing() {
        assertFalse(trustedKeys.isTrusted(100, "AB:CD"));
    }

    @Test
    public void trustShouldPersistAndBeRememberedAfterReload() {
        trustedKeys.trust(100, "AB:CD:EF");

        // New instance loading the same file should see the trust.
        final TrustedKeys reloaded = new TrustedKeys(tempFile.toString());
        reloaded.load();
        assertTrue(reloaded.isTrusted(100, "AB:CD:EF"));
    }

    @Test
    public void wrongFingerprintShouldNotBeTrusted() {
        trustedKeys.trust(100, "AB:CD:EF");
        assertFalse(trustedKeys.isTrusted(100, "AB:CD:99"));
        assertFalse(trustedKeys.isTrusted(101, "AB:CD:EF"));
    }

    @Test
    public void untrustShouldRemoveEntry() {
        trustedKeys.trust(100, "AB:CD:EF");
        assertTrue(trustedKeys.isTrusted(100, "AB:CD:EF"));

        trustedKeys.untrust(100);
        assertFalse(trustedKeys.isTrusted(100, "AB:CD:EF"));
    }
}
