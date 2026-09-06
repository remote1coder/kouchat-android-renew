
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
 * UI callback for asking whether the user trusts a peer's public key.
 *
 * <p>Implemented by each user interface (Swing dialog, console prompt) and
 * registered on the controller. Kept out of {@code UserInterface} so that UIs
 * that do not support interactive prompts (e.g. the test client) are not forced
 * to implement it.</p>
 *
 * @author Christian Ihle
 */
public interface TrustPrompt {

    /**
     * Asks the user whether to trust the given peer's key. Implementations may
     * show a dialog asynchronously and invoke the callback when decided.
     *
     * @param peer The peer whose key is being trusted.
     * @param fingerprint The peer's key fingerprint, for display.
     * @param callback Invoked with the user's decision.
     */
    void requestTrust(User peer, String fingerprint, TrustCallback callback);
}
