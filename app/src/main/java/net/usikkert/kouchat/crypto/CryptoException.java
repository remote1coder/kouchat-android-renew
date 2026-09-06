
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

/**
 * Thrown when an encryption, decryption, wrapping, signing or verification
 * operation fails. Wraps the underlying {@link java.security} /
 * {@link javax.crypto} checked exceptions so callers can treat crypto
 * failures uniformly.
 *
 * @author Christian Ihle
 */
public class CryptoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructor.
     *
     * @param message The detail message.
     * @param cause The underlying crypto/security exception.
     */
    public CryptoException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
