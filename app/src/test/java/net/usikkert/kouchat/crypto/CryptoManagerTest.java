
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
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Test;

import net.usikkert.kouchat.misc.User;

/**
 * Test of {@link CryptoManager}: simulates a two-sided handshake between Alice
 * and Bob using a queued mock sink, and verifies the encrypted channel activates
 * only after both sides trust.
 *
 * @author Christian Ihle
 */
public class CryptoManagerTest {

    private final List<Path> tempFiles = new ArrayList<>();

    private TrustedKeys newTempTrustedKeys() throws Exception {
        final Path file = Files.createTempFile("kouchat-trust", ".ini");
        Files.delete(file);
        tempFiles.add(file);
        final TrustedKeys tk = new TrustedKeys(file.toString());
        tk.load();
        return tk;
    }

    @After
    public void tearDown() throws Exception {
        for (final Path f : tempFiles) {
            Files.deleteIfExists(f);
        }
    }

    @Test
    public void handshakeShouldActivateChannelForBothSides() throws Exception {
        final User alice = new User("Alice", 100);
        final User bob = new User("Bob", 200);

        final Identity aliceId = tempIdentity("alice");
        final Identity bobId = tempIdentity("bob");

        final TrustedKeys aliceTrusted = newTempTrustedKeys();
        final TrustedKeys bobTrusted = newTempTrustedKeys();

        final QueuedSink sink = new QueuedSink();

        final CryptoManager aliceMgr = new CryptoManager(aliceId, aliceTrusted, sink);
        aliceMgr.setLocalCode(100);
        aliceMgr.setTrustPrompt(autoTrust(aliceMgr));

        final CryptoManager bobMgr = new CryptoManager(bobId, bobTrusted, sink);
        bobMgr.setLocalCode(200);
        bobMgr.setTrustPrompt(autoTrust(bobMgr));

        sink.register(alice, aliceMgr);
        sink.register(bob, bobMgr);

        // Alice initiates the handshake with Bob.
        aliceMgr.initiateHandshake(bob);

        // Pump queued messages until the queue drains.
        sink.pump();

        assertTrue("Alice should have an active channel with Bob",
                   aliceMgr.isChannelActive(bob));
        assertTrue("Bob should have an active channel with Alice",
                   bobMgr.isChannelActive(alice));
        assertFalse(aliceMgr.isUnsupported(bob));
    }

    @Test
    public void encryptedMessageShouldRoundtripThroughChannel() throws Exception {
        final User alice = new User("Alice", 100);
        final User bob = new User("Bob", 200);

        final Identity aliceId = tempIdentity("alice");
        final Identity bobId = tempIdentity("bob");

        final QueuedSink sink = new QueuedSink();
        final CryptoManager aliceMgr = new CryptoManager(aliceId, newTempTrustedKeys(), sink);
        aliceMgr.setLocalCode(100);
        aliceMgr.setTrustPrompt(autoTrust(aliceMgr));

        final CryptoManager bobMgr = new CryptoManager(bobId, newTempTrustedKeys(), sink);
        bobMgr.setLocalCode(200);
        bobMgr.setTrustPrompt(autoTrust(bobMgr));

        sink.register(alice, aliceMgr);
        sink.register(bob, bobMgr);

        aliceMgr.initiateHandshake(bob);
        sink.pump();

        final String ciphertext = aliceMgr.encryptForPeer(bob, "hello bob");
        assertNotNull(ciphertext);
        assertEquals("hello bob", bobMgr.decryptFromPeer(alice, ciphertext));
    }

    @Test
    public void decliningTrustShouldNotActivateChannel() throws Exception {
        final User bob = new User("Bob", 200);

        final Identity aliceId = tempIdentity("alice");
        final Identity bobId = tempIdentity("bob");

        final QueuedSink sink = new QueuedSink();
        final CryptoManager aliceMgr = new CryptoManager(aliceId, newTempTrustedKeys(), sink);
        aliceMgr.setLocalCode(100);
        aliceMgr.setTrustPrompt((peer, fp, cb) -> cb.onDecision(false)); // decline

        final CryptoManager bobMgr = new CryptoManager(bobId, newTempTrustedKeys(), sink);
        bobMgr.setLocalCode(200);
        bobMgr.setTrustPrompt(autoTrust(bobMgr));

        final User alice = new User("Alice", 100);
        sink.register(alice, aliceMgr);
        sink.register(bob, bobMgr);

        aliceMgr.initiateHandshake(bob);
        sink.pump();

        assertFalse("Alice declined, so her channel must not be active",
                    aliceMgr.isChannelActive(bob));
        assertTrue(aliceMgr.isRejected(bob));
    }

    private Identity tempIdentity(final String prefix) throws Exception {
        final Path priv = Files.createTempFile("kouchat-" + prefix + "-priv", ".key");
        final Path pub = Files.createTempFile("kouchat-" + prefix + "-pub", ".key");
        Files.delete(priv);
        Files.delete(pub);
        tempFiles.add(priv);
        tempFiles.add(pub);
        return new Identity(priv.toString(), pub.toString());
    }

    private static TrustPrompt autoTrust(final CryptoManager mgr) {
        return (peer, fp, cb) -> {
            // Auto-trust on the same thread. The manager persists + proceeds.
            cb.onDecision(true);
        };
    }

    /**
     * Mock sink that queues outbound handshake messages and delivers them to the
     * target manager when {@link #pump()} is called. Models async delivery so the
     * test driver controls the ordering and avoids deep re-entrancy.
     */
    private static final class QueuedSink implements CryptoMessageSink {
        private final java.util.Map<Integer, CryptoManager> managers = new java.util.HashMap<>();
        private final java.util.Map<Integer, User> users = new java.util.HashMap<>();
        private final List<Runnable> queue = new ArrayList<>();

        void register(final User user, final CryptoManager mgr) {
            managers.put(user.getCode(), mgr);
            users.put(user.getCode(), user);
        }

        void pump() {
            while (!queue.isEmpty()) {
                final Runnable r = queue.remove(0);
                r.run();
            }
        }

        private void deliverTo(final User recipient, final Delivery delivery) {
            final CryptoManager mgr = managers.get(recipient.getCode());
            if (mgr != null) {
                final User sender = senderOf(recipient);
                queue.add(() -> delivery.deliver(mgr, sender));
            }
        }

        /**
         * The sender of a message addressed to {@code recipient}: the other
         * registered user (the one whose code differs from the recipient's).
         */
        private User senderOf(final User recipient) {
            for (final int code : users.keySet()) {
                if (code != recipient.getCode()) {
                    return users.get(code);
                }
            }

            return recipient;
        }

        @Override
        public void sendPubKey(final User peer, final String publicKeyBase64) {
            deliverTo(peer, (m, sender) -> m.onPeerPubKey(sender, publicKeyBase64));
        }

        @Override
        public void sendKeyReq(final User peer) {
            deliverTo(peer, (m, sender) -> m.onKeyReq(sender));
        }

        @Override
        public void sendKeyTrust(final User peer, final String wrappedKey, final String signature) {
            deliverTo(peer, (m, sender) -> m.onKeyTrust(sender, wrappedKey, signature));
        }

        @Override
        public void sendKeyTrustAck(final User peer) {
            deliverTo(peer, (m, sender) -> m.onKeyTrustAck(sender));
        }

        @Override
        public void sendKeyReject(final User peer) {
            deliverTo(peer, (m, sender) -> m.onKeyReject(sender));
        }

        @FunctionalInterface
        private interface Delivery {
            void deliver(CryptoManager mgr, User sender);
        }
    }
}
