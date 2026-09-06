
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

import java.security.PublicKey;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.SecretKey;

import net.usikkert.kouchat.misc.User;
import net.usikkert.kouchat.util.Validate;

/**
 * Orchestrates the end-to-end encrypted handshake and per-peer channels.
 *
 * <p>Each peer has a {@link PeerCryptoState} tracking: the peer's public key,
 * whether the local user has trusted it, whether the peer has signaled trust
 * back, and the negotiated AES-256 session key. The encrypted channel is
 * considered <em>active</em> only once both sides have signaled trust and a
 * session key is in place - satisfying the "both must trust" requirement.</p>
 *
 * <p>Role assignment is deterministic: the side with the lower user code is the
 * <em>initiator</em> and generates + sends the AES session key inside
 * {@code KEYTRUST}; the higher-code side is the <em>responder</em> and sends a
 * payload-less {@code KEYTRUSTACK}. This avoids session-key races.</p>
 *
 * <p>Trust decisions are persistent: a previously-trusted key fingerprint is
 * not re-prompted (see {@link TrustedKeys}). If a peer does not respond within
 * the handshake timeout, it is marked {@code unsupported} and the controller
 * falls back to plaintext for that peer.</p>
 *
 * @author Christian Ihle
 */
public class CryptoManager {

    private static final Logger LOG = Logger.getLogger(CryptoManager.class.getName());

    /** Milliseconds to wait for a peer's key exchange response before falling back. */
    static final long HANDSHAKE_TIMEOUT_MS = 10_000;

    private final Identity identity;
    private final TrustedKeys trustedKeys;
    private final CryptoMessageSink sink;
    private final Timer timeoutTimer;

    @org.jetbrains.annotations.Nullable
    private TrustPrompt trustPrompt;

    @org.jetbrains.annotations.Nullable
    private CryptoStatusListener statusListener;

    private final Map<Integer, PeerCryptoState> states = new ConcurrentHashMap<>();

    /**
     * Constructor.
     *
     * @param identity The local RSA identity.
     * @param trustedKeys The persisted trust store.
     * @param sink The network sink for sending handshake messages.
     */
    public CryptoManager(final Identity identity, final TrustedKeys trustedKeys, final CryptoMessageSink sink) {
        Validate.notNull(identity, "Identity can not be null");
        Validate.notNull(trustedKeys, "Trusted keys can not be null");
        Validate.notNull(sink, "Sink can not be null");

        this.identity = identity;
        this.trustedKeys = trustedKeys;
        this.sink = sink;
        this.timeoutTimer = new Timer("CryptoHandshakeTimeout", true);

        trustedKeys.load();
    }

    /**
     * Sets the UI callback used to ask the user about trusting a peer's key.
     *
     * @param trustPrompt The trust prompt callback.
     */
    public void setTrustPrompt(final TrustPrompt trustPrompt) {
        this.trustPrompt = trustPrompt;
    }

    /**
     * Sets the status listener notified when a peer is marked unsupported.
     *
     * @param statusListener The status listener.
     */
    public void setStatusListener(final CryptoStatusListener statusListener) {
        this.statusListener = statusListener;
    }

    /**
     * Starts (or re-checks) the handshake with a peer. Sends a key request and
     * this client's public key, and schedules a fallback timeout.
     *
     * @param peer The peer to establish an encrypted channel with.
     */
    public synchronized void initiateHandshake(final User peer) {
        Validate.notNull(peer, "Peer can not be null");

        final PeerCryptoState state = stateFor(peer);
        state.handshakeInitiated = true;

        if (state.channel != null || state.unsupported) {
            return;
        }

        sink.sendKeyReq(peer);
        sink.sendPubKey(peer, identity.getPublicKeyBase64());

        timeoutTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                markUnsupportedIfStuck(peer.getCode());
            }
        }, HANDSHAKE_TIMEOUT_MS);
    }

    /**
     * Called when the peer's public key arrives. Stores it and prompts for trust
     * (unless already trusted).
     *
     * @param peer The peer.
     * @param publicKeyBase64 The peer's base64 public key.
     */
    public synchronized void onPeerPubKey(final User peer, final String publicKeyBase64) {
        Validate.notNull(peer, "Peer can not be null");
        final PeerCryptoState state = stateFor(peer);

        try {
            final PublicKey pubKey = Identity.publicKeyFromBase64(publicKeyBase64);
            final String fingerprint = CryptoUtils.fingerprint(pubKey);
            state.peerPublicKey = pubKey;
            state.fingerprint = fingerprint;
            state.unsupported = false; // peer speaks crypto

            // If a KEYTRUST arrived before this public key (race), apply it now that we
            // have the key to verify the signature against.
            if (state.pendingWrappedKey != null) {
                applyKeyTrust(peer, state.pendingWrappedKey, state.pendingSignature);
                state.pendingWrappedKey = null;
                state.pendingSignature = null;
            }

            if (trustedKeys.isTrusted(peer.getCode(), fingerprint)) {
                state.iTrusted = true;
                onLocalTrustGranted(peer);
            }

            else if (!state.promptShown && trustPrompt != null) {
                state.promptShown = true;
                trustPrompt.requestTrust(peer, CryptoUtils.groupFingerprint(fingerprint), new TrustCallback() {
                    @Override
                    public void onDecision(final boolean trusted) {
                        handleTrustDecision(peer, trusted);
                    }
                });
            }
        }

        catch (final CryptoException e) {
            LOG.log(Level.WARNING, "Could not parse peer public key from " + peer.getNick() + ": " + e, e);
        }
    }

    /**
     * Called when a key request arrives from a peer. Replies with our public key.
     *
     * @param peer The requesting peer.
     */
    public synchronized void onKeyReq(final User peer) {
        Validate.notNull(peer, "Peer can not be null");
        final PeerCryptoState state = stateFor(peer);
        state.unsupported = false;
        sink.sendPubKey(peer, identity.getPublicKeyBase64());
    }

    /**
     * Called when the local user grants trust for a peer's key (from the trust dialog).
     *
     * @param peer The peer.
     */
    public synchronized void onLocalTrustGranted(final User peer) {
        final PeerCryptoState state = stateFor(peer);

        if (state.peerPublicKey == null || state.fingerprint == null) {
            // Peer key not received yet; trust will be applied when it arrives.
            return;
        }

        trustedKeys.trust(peer.getCode(), state.fingerprint);
        state.iTrusted = true;

        try {
            if (isInitiator(peer)) {
                // Initiator generates the session key and sends it, signed.
                final SecretKey sessionKey = CryptoUtils.generateAesKey();
                state.sessionKey = sessionKey;
                final String wrapped = CryptoUtils.wrapSessionKey(state.peerPublicKey, sessionKey);
                final String sig = CryptoUtils.sign(identity.getPrivateKey(), signedPayload(peer, wrapped));
                sink.sendKeyTrust(peer, wrapped, sig);
            }

            else {
                // Responder just signals trust back.
                sink.sendKeyTrustAck(peer);
            }
        }

        catch (final RuntimeException e) {
            // A crypto/network failure here must not crash the app (the callback may run on
            // a UI thread). Log and leave the channel inactive; the peer will time out and
            // fall back to plaintext.
            LOG.log(Level.WARNING, "Failed to complete key exchange with " + peer.getNick() + ": " + e, e);
        }

        tryActivate(peer);
    }

    /**
     * Called when KEYTRUST arrives (responder side): the initiator trusts us and
     * carries the wrapped session key.
     *
     * @param peer The peer.
     * @param wrappedKey Base64 RSA-OAEP-wrapped AES session key.
     * @param signature Base64 SHA256withRSA signature for authenticity.
     */
    public synchronized void onKeyTrust(final User peer, final String wrappedKey, final String signature) {
        Validate.notNull(peer, "Peer can not be null");
        final PeerCryptoState state = stateFor(peer);
        state.unsupported = false;
        state.peerTrustedMe = true;

        if (state.peerPublicKey == null) {
            // Key not received yet; remember and apply later.
            state.pendingWrappedKey = wrappedKey;
            state.pendingSignature = signature;
            return;
        }

        applyKeyTrust(peer, wrappedKey, signature);
    }

    private void applyKeyTrust(final User peer, final String wrappedKey, final String signature) {
        final PeerCryptoState state = stateFor(peer);

        if (state.peerPublicKey == null) {
            return;
        }

        if (!CryptoUtils.verify(state.peerPublicKey, signedPayload(peer, wrappedKey), signature)) {
            LOG.log(Level.WARNING, "Bad signature on KEYTRUST from " + peer.getNick() + ", ignoring.");
            return;
        }

        try {
            state.sessionKey = CryptoUtils.unwrapSessionKey(identity.getPrivateKey(), wrappedKey);
        }

        catch (final CryptoException e) {
            LOG.log(Level.WARNING, "Could not unwrap session key from " + peer.getNick() + ": " + e, e);
            return;
        }

        tryActivate(peer);
    }

    /**
     * Called when KEYTRUSTACK arrives (initiator side): the responder trusts us.
     *
     * @param peer The peer.
     */
    public synchronized void onKeyTrustAck(final User peer) {
        Validate.notNull(peer, "Peer can not be null");
        final PeerCryptoState state = stateFor(peer);
        state.unsupported = false;
        state.peerTrustedMe = true;
        tryActivate(peer);
    }

    /**
     * Called when the peer declines trust.
     *
     * @param peer The peer.
     */
    public synchronized void onKeyReject(final User peer) {
        final PeerCryptoState state = stateFor(peer);
        state.peerTrustedMe = false;
        state.rejected = true;
    }

    private synchronized void handleTrustDecision(final User peer, final boolean trusted) {
        if (trusted) {
            onLocalTrustGranted(peer);
        }

        else {
            final PeerCryptoState state = stateFor(peer);
            state.iTrusted = false;
            state.rejected = true;
            sink.sendKeyReject(peer);
        }
    }

    private synchronized void markUnsupportedIfStuck(final int code) {
        final PeerCryptoState state = states.get(code);
        if (state == null) {
            return;
        }

        if (state.peerPublicKey == null && state.channel == null) {
            state.unsupported = true;
            LOG.log(Level.FINE, "Peer " + code + " did not respond to key exchange; falling back to plaintext.");

            final CryptoStatusListener listener = statusListener;
            if (listener != null && state.peer != null) {
                listener.peerUnsupported(state.peer);
            }
        }
    }

    private void tryActivate(final User peer) {
        final PeerCryptoState state = stateFor(peer);

        if (state.channel != null) {
            return;
        }

        if (state.iTrusted && state.peerTrustedMe && state.sessionKey != null) {
            state.channel = new EncryptedChannel(state.sessionKey);
            LOG.log(Level.FINE, "Encrypted channel activated with " + peer.getNick());
        }
    }

    /**
     * Encrypts a plaintext message for a peer using the active channel.
     *
     * @param peer The peer.
     * @param plaintext The message.
     * @return Base64 ciphertext, or {@code null} if the channel is not active.
     */
    public synchronized String encryptForPeer(final User peer, final String plaintext) {
        final PeerCryptoState state = states.get(peer.getCode());
        if (state == null || state.channel == null) {
            return null;
        }

        return state.channel.encrypt(plaintext);
    }

    /**
     * Decrypts a base64 ciphertext from a peer.
     *
     * @param peer The peer.
     * @param ciphertext Base64 ciphertext.
     * @return The plaintext, or {@code null} if the channel is not active.
     */
    public synchronized String decryptFromPeer(final User peer, final String ciphertext) {
        final PeerCryptoState state = states.get(peer.getCode());
        if (state == null || state.channel == null) {
            return null;
        }

        try {
            return state.channel.decrypt(ciphertext);
        }

        catch (final CryptoException e) {
            LOG.log(Level.WARNING, "Failed to decrypt message from " + peer.getNick() + ": " + e, e);
            return null;
        }
    }

    /**
     * Whether a UI trust prompt has been registered. If not, the encrypted handshake
     * can never complete (the user can't be asked to trust keys), so the controller
     * should fall back to plaintext immediately instead of waiting for the timeout.
     *
     * @return True if a trust prompt is registered.
     */
    public synchronized boolean hasTrustPrompt() {
        return trustPrompt != null;
    }

    /**
     * Whether the encrypted channel with the peer is active.
     *
     * @param peer The peer.
     * @return True if messages can be sent encrypted.
     */
    public synchronized boolean isChannelActive(final User peer) {
        final PeerCryptoState state = states.get(peer.getCode());
        return state != null && state.channel != null;
    }

    /**
     * Whether the peer is marked unsupported (no crypto response).
     *
     * @param peer The peer.
     * @return True if the peer should use plaintext fallback.
     */
    public synchronized boolean isUnsupported(final User peer) {
        final PeerCryptoState state = states.get(peer.getCode());
        return state != null && state.unsupported;
    }

    /**
     * Whether a handshake with the peer has been started but not finished (or failed).
     * Used to avoid repeatedly showing "establishing connection" notices.
     *
     * @param peer The peer.
     * @return True if the handshake is pending.
     */
    public synchronized boolean isHandshakePending(final User peer) {
        final PeerCryptoState state = states.get(peer.getCode());
        return state != null && state.handshakeInitiated && state.channel == null
                && !state.unsupported && !state.rejected;
    }

    /**
     * Whether the peer declined trust.
     *
     * @param peer The peer.
     * @return True if trust was declined.
     */
    public synchronized boolean isRejected(final User peer) {
        final PeerCryptoState state = states.get(peer.getCode());
        return state != null && state.rejected;
    }

    private boolean isInitiator(final User peer) {
        return identityLocalCode() < peer.getCode();
    }

    /**
     * The local user code. Overridden in tests; production reads it from a supplier
     * set by the controller to avoid a direct dependency on {@code Settings}.
     */
    private int localCode;

    /**
     * Sets the local user code, used for initiator/responder tie-breaking.
     *
     * @param code The local user's code.
     */
    public void setLocalCode(final int code) {
        this.localCode = code;
    }

    private int identityLocalCode() {
        return localCode;
    }

    private byte[] signedPayload(final User peer, final String wrappedKey) {
        // Canonical (order-independent) form so both sides compute the same bytes:
        // the initiator signs it and the responder verifies it without knowing who
        // is local vs. peer.
        final int low = Math.min(localCode, peer.getCode());
        final int high = Math.max(localCode, peer.getCode());
        return (low + ":" + high + ":" + wrappedKey).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private PeerCryptoState stateFor(final User peer) {
        PeerCryptoState state = states.get(peer.getCode());
        if (state == null) {
            state = new PeerCryptoState();
            state.peer = peer;
            final PeerCryptoState existing = states.putIfAbsent(peer.getCode(), state);
            if (existing != null) {
                state = existing;
            }
        }

        // If we received KEYTRUST before the peer's pub key, apply it now.
        if (state.peerPublicKey != null && state.pendingWrappedKey != null && state.sessionKey == null) {
            applyKeyTrust(peer, state.pendingWrappedKey, state.pendingSignature);
            state.pendingWrappedKey = null;
            state.pendingSignature = null;
        }

        return state;
    }

    /**
     * Per-peer handshake state.
     */
    static final class PeerCryptoState {
        @org.jetbrains.annotations.Nullable
        User peer;

        @org.jetbrains.annotations.Nullable
        PublicKey peerPublicKey;
        @org.jetbrains.annotations.Nullable
        String fingerprint;
        @org.jetbrains.annotations.Nullable
        SecretKey sessionKey;
        @org.jetbrains.annotations.Nullable
        EncryptedChannel channel;
        boolean iTrusted;
        boolean peerTrustedMe;
        boolean unsupported;
        boolean rejected;
        boolean promptShown;
        boolean handshakeInitiated;
        @org.jetbrains.annotations.Nullable
        String pendingWrappedKey;
        @org.jetbrains.annotations.Nullable
        String pendingSignature;
    }
}
