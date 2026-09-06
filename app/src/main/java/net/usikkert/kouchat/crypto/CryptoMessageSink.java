
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

import net.usikkert.kouchat.misc.User;

/**
 * Used by {@link CryptoManager} to send the key-exchange handshake messages,
 * without depending on the network layer directly.
 *
 * <p>The controller wires an implementation that delegates to
 * {@code NetworkMessages}, sending each message over the TCP unicast path so
 * the large RSA payloads (public key, wrapped session key) fit the
 * {@code writeUTF} framing.</p>
 *
 * @author Christian Ihle
 */
public interface CryptoMessageSink {

    /**
     * Sends this client's public key (base64 X.509) to the peer.
     *
     * @param peer The peer to send to.
     * @param publicKeyBase64 The base64-encoded public key.
     */
    void sendPubKey(User peer, String publicKeyBase64);

    /**
     * Asks the peer to send its public key.
     *
     * @param peer The peer to request from.
     */
    void sendKeyReq(User peer);

    /**
     * Sends the RSA-wrapped AES session key plus a signature, so the peer can
     * activate the encrypted channel once it also trusts this client.
     *
     * @param peer The peer to send to.
     * @param wrappedKey Base64 RSA-OAEP-wrapped AES session key.
     * @param signature Base64 SHA256withRSA signature.
     */
    void sendKeyTrust(User peer, String wrappedKey, String signature);

    /**
     * Acknowledges that this client trusts the peer's key and has the session key.
     *
     * @param peer The peer to acknowledge to.
     */
    void sendKeyTrustAck(User peer);

    /**
     * Tells the peer that this client declined to trust its key.
     *
     * @param peer The peer to reject.
     */
    void sendKeyReject(User peer);
}
