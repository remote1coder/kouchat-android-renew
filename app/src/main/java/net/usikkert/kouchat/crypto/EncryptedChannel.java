
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

import java.util.concurrent.atomic.AtomicLong;

import javax.crypto.SecretKey;

import net.usikkert.kouchat.util.Validate;

/**
 * A unidirectional encrypted channel for one peer: holds the negotiated AES-256
 * session key and encrypts/decrypts individual messages with AES-GCM.
 *
 * <p>Each message uses a fresh random IV (see {@link CryptoUtils#generateIv()}),
 * so IVs are never reused under the same key. The class is thread-safe.</p>
 *
 * @author Christian Ihle
 */
public class EncryptedChannel {

    private final SecretKey sessionKey;
    private final AtomicLong messageCounter = new AtomicLong(0);

    /**
     * Constructor.
     *
     * @param sessionKey The negotiated AES-256 session key for this peer.
     */
    public EncryptedChannel(final SecretKey sessionKey) {
        Validate.notNull(sessionKey, "Session key can not be null");
        this.sessionKey = sessionKey;
    }

    /**
     * Encrypts a plaintext message for the peer.
     *
     * @param plaintext The message to encrypt.
     * @return Base64-encoded {@code IV || ciphertext || tag}.
     */
    public String encrypt(final String plaintext) {
        messageCounter.incrementAndGet();
        return CryptoUtils.encryptAesGcm(sessionKey, plaintext);
    }

    /**
     * Decrypts a base64 message from the peer.
     *
     * @param base64 The base64 blob from the peer.
     * @return The plaintext message.
     */
    public String decrypt(final String base64) {
        return CryptoUtils.decryptAesGcm(sessionKey, base64);
    }

    /**
     * Returns how many messages have been encrypted on this channel.
     *
     * @return The encrypted message count.
     */
    public long getMessageCount() {
        return messageCounter.get();
    }
}
