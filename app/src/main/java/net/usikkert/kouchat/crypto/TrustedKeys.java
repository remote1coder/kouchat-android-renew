
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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.usikkert.kouchat.Constants;
import net.usikkert.kouchat.util.PropertyTools;
import net.usikkert.kouchat.util.Validate;

/**
 * Persists which peer public keys the local user has chosen to trust.
 *
 * <p>Stored as {@code ~/.kouchat/trusted_keys.ini}, a properties file mapping
 * a user code to the SHA-256 fingerprint of the trusted public key. This makes
 * trust persistent across restarts, so each peer only needs to be trusted once
 * (similar to SSH {@code known_hosts} or Signal safety numbers).</p>
 *
 * @author Christian Ihle
 */
public class TrustedKeys {

    private static final Logger LOG = Logger.getLogger(TrustedKeys.class.getName());

    /** Full path to the trusted keys file. */
    public static final String TRUSTED_KEYS_FILE = Constants.APP_FOLDER + "trusted_keys.ini";

    private static final String COMMENT = "KouChat trusted peer keys";

    private final PropertyTools propertyTools = new PropertyTools();

    private final String filePath;

    private Properties trusted;

    /**
     * Constructor. Uses the default trusted keys file location.
     */
    public TrustedKeys() {
        this(TRUSTED_KEYS_FILE);
    }

    /**
     * Constructor for tests, allowing a custom file path.
     *
     * @param filePath The file path to load/save trusted keys from.
     */
    TrustedKeys(final String filePath) {
        this.filePath = filePath;
    }

    /**
     * Loads the trusted keys from disk into memory.
     */
    public synchronized void load() {
        trusted = new Properties();

        try {
            trusted = propertyTools.loadProperties(filePath);
        }

        catch (final FileNotFoundException e) {
            LOG.log(Level.FINE, "No trusted keys file yet, starting empty.");
        }

        catch (final IOException e) {
            LOG.log(Level.SEVERE, e.toString(), e);
        }
    }

    /**
     * Checks if a peer's key is trusted. The fingerprint must match exactly, so a
     * changed key (e.g. from a different client) is not silently trusted.
     *
     * @param userCode The peer's user code.
     * @param fingerprint The peer's key fingerprint.
     * @return True if this exact key was previously trusted.
     */
    public synchronized boolean isTrusted(final int userCode, final String fingerprint) {
        Validate.notNull(fingerprint, "Fingerprint can not be null");

        if (trusted == null) {
            load();
        }

        final String stored = trusted.getProperty(String.valueOf(userCode));
        return fingerprint.equals(stored);
    }

    /**
     * Records trust for a peer's key and persists it.
     *
     * @param userCode The peer's user code.
     * @param fingerprint The peer's key fingerprint.
     */
    public synchronized void trust(final int userCode, final String fingerprint) {
        Validate.notNull(fingerprint, "Fingerprint can not be null");

        if (trusted == null) {
            load();
        }

        trusted.setProperty(String.valueOf(userCode), fingerprint);
        save();
    }

    /**
     * Removes trust for a peer.
     *
     * @param userCode The peer's user code.
     */
    public synchronized void untrust(final int userCode) {
        if (trusted == null) {
            load();
        }

        if (trusted.remove(String.valueOf(userCode)) != null) {
            save();
        }
    }

    private void save() {
        try {
            propertyTools.saveProperties(filePath, trusted, COMMENT);
        }

        catch (final IOException e) {
            LOG.log(Level.SEVERE, "Could not save trusted keys: " + e, e);
        }
    }
}
