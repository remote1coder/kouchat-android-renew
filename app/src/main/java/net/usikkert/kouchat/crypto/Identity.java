
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.usikkert.kouchat.Constants;
import net.usikkert.kouchat.util.IOTools;
import net.usikkert.kouchat.util.Validate;

/**
 * Owns the local long-term RSA-4096 identity key pair.
 *
 * <p>The keys are generated on first use and persisted to
 * {@code ~/.kouchat/identity.key} (PKCS#8, base64) and
 * {@code ~/.kouchat/identity.pub} (X.509, base64) so the same identity is
 * reused across restarts. This keeps the public-key fingerprint stable, so a
 * peer only needs to trust it once.</p>
 *
 * @author Christian Ihle
 */
public class Identity {

    private static final Logger LOG = Logger.getLogger(Identity.class.getName());

    /** Full path to the private key file. */
    static final String PRIVATE_KEY_FILE = Constants.APP_FOLDER + "identity.key";

    /** Full path to the public key file. */
    static final String PUBLIC_KEY_FILE = Constants.APP_FOLDER + "identity.pub";

    private final String privateKeyFile;
    private final String publicKeyFile;

    private final KeyPair keyPair;

    /**
     * Constructor. Loads the default identity from {@code ~/.kouchat/}, or generates
     * a new one on first use.
     */
    public Identity() {
        this(PRIVATE_KEY_FILE, PUBLIC_KEY_FILE);
    }

    /**
     * Constructor for tests, allowing custom key file paths.
     *
     * @param privateKeyFile Path to the private key file.
     * @param publicKeyFile Path to the public key file.
     */
    Identity(final String privateKeyFile, final String publicKeyFile) {
        this.privateKeyFile = privateKeyFile;
        this.publicKeyFile = publicKeyFile;
        keyPair = loadOrGenerate();
    }

    private KeyPair loadOrGenerate() {
        try {
            final KeyPair existing = load();
            if (existing != null) {
                return existing;
            }
        }

        catch (final Exception e) {
            LOG.log(Level.WARNING, "Could not load identity, generating a new one: " + e, e);
        }

        final KeyPair generated = CryptoUtils.generateRsaKeyPair();
        save(generated);
        return generated;
    }

    private KeyPair load() throws Exception {
        final File privFile = new File(privateKeyFile);
        final File pubFile = new File(publicKeyFile);

        if (!privFile.exists() || !pubFile.exists()) {
            return null;
        }

        final KeyFactory factory = KeyFactory.getInstance("RSA");

        final byte[] privBytes = CryptoUtils.base64Decode(readFile(privFile).trim());
        final PrivateKey privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(privBytes));

        final byte[] pubBytes = CryptoUtils.base64Decode(readFile(pubFile).trim());
        final PublicKey publicKey = factory.generatePublic(new X509EncodedKeySpec(pubBytes));

        return new KeyPair(publicKey, privateKey);
    }

    private void save(final KeyPair pair) {
        try {
            final File privFile = new File(privateKeyFile);
            final File pubFile = new File(publicKeyFile);

            final File parent = privFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            final String privBase64 = CryptoUtils.base64Encode(pair.getPrivate().getEncoded());
            final String pubBase64 = CryptoUtils.base64Encode(pair.getPublic().getEncoded());

            writeFile(privFile, privBase64);
            writeFile(pubFile, pubBase64);
        }

        catch (final IOException e) {
            LOG.log(Level.SEVERE, "Could not save identity keys: " + e, e);
        }
    }

    private static String readFile(final File file) throws IOException {
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        FileInputStream in = null;

        try {
            in = new FileInputStream(file);

            final byte[] block = new byte[4096];
            int read;

            while ((read = in.read(block)) != -1) {
                buffer.write(block, 0, read);
            }
        }

        finally {
            new IOTools().close(in);
        }

        return new String(buffer.toByteArray(), "UTF-8");
    }

    private static void writeFile(final File file, final String content) throws IOException {
        FileOutputStream out = null;

        try {
            out = new FileOutputStream(file);
            out.write(content.getBytes("UTF-8"));
        }

        finally {
            new IOTools().close(out);
        }
    }

    /**
     * Returns the local RSA public key.
     *
     * @return The local public key.
     */
    public PublicKey getPublicKey() {
        return keyPair.getPublic();
    }

    /**
     * Returns the local RSA private key.
     *
     * @return The local private key.
     */
    public PrivateKey getPrivateKey() {
        return keyPair.getPrivate();
    }

    /**
     * Returns the public key as base64-encoded X.509 DER, for sending over the network.
     *
     * @return The base64-encoded public key.
     */
    public String getPublicKeyBase64() {
        return CryptoUtils.base64Encode(keyPair.getPublic().getEncoded());
    }

    /**
     * Returns the SHA-256 fingerprint of the local public key, for display.
     *
     * @return The fingerprint.
     */
    public String getFingerprint() {
        return CryptoUtils.fingerprint(keyPair.getPublic());
    }

    /**
     * Reconstructs a public key from a base64 X.509 encoding received from a peer.
     *
     * @param base64 The base64-encoded public key.
     * @return The reconstructed public key.
     */
    public static PublicKey publicKeyFromBase64(final String base64) {
        Validate.notNull(base64, "Base64 public key can not be null");

        try {
            final byte[] bytes = CryptoUtils.base64Decode(base64);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(bytes));
        }

        catch (final Exception e) {
            throw new CryptoException("Failed to decode peer public key", e);
        }
    }
}
