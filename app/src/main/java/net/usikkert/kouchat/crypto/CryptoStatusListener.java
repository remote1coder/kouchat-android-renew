
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
 * Callback for status changes in the crypto handshake that the UI should tell the
 * user about (so they are never left guessing whether encryption is active).
 *
 * @author Christian Ihle
 */
public interface CryptoStatusListener {

    /**
     * Called when a peer is marked unsupported (no response to the key exchange within
     * the timeout). The UI should inform the user that communication with this peer
     * will be in plaintext.
     *
     * @param peer The peer that does not support encryption.
     */
    void peerUnsupported(User peer);
}
