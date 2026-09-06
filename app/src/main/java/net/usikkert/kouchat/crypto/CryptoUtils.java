
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

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import net.usikkert.kouchat.util.Validate;

/**
 * Pure cryptographic helpers for the end-to-end encrypted chat.
 *
 * <p>Uses only APIs available under {@code --release 8} and on Android:</p>
 * <ul>
 *   <li>RSA-4096 for long-term identity keys (Ed25519/X25519 are Java 15+).</li>
 *   <li>RSA-OAEP (SHA-256, MGF1) to wrap a per-conversation AES-256 session key.</li>
 *   <li>SHA256withRSA to sign the key exchange (authenticity / MITM detection).</li>
 *   <li>AES-256-GCM (12-byte IV, 128-bit tag) for message confidentiality.</li>
 *   <li>SHA-256 fingerprint of the public key for the trust dialog.</li>
 * </ul>
 *
 * <p>All ciphertexts are base64-encoded so they fit the text-based network
 * message format ({@code code!TYPE#nick:payload}) and the UTF-8 TCP framing.</p>
 *
 * @author Christian Ihle
 */
public final class CryptoUtils {

    /** RSA key size, in bits. */
    public static final int RSA_KEY_SIZE = 4096;

    /** AES session key size, in bits. */
    public static final int AES_KEY_SIZE = 256;

    /** GCM IV (nonce) size, in bytes. */
    public static final int GCM_IV_SIZE = 12;

    /** GCM authentication tag size, in bits. */
    public static final int GCM_TAG_BITS = 128;

    /** RSA cipher used to wrap the AES session key. */
    static final String RSA_OAEP_CIPHER = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";

    /** AES cipher used for message encryption. */
    static final String AES_GCM_CIPHER = "AES/GCM/NoPadding";

    /** Signature algorithm used to authenticate the key exchange. */
    static final String SIGNATURE_ALGORITHM = "SHA256withRSA";

    private static final SecureRandom RNG = new SecureRandom();

    private CryptoUtils() {

    }

    /**
     * Generates a new RSA-4096 key pair. This takes roughly a second.
     *
     * @return A fresh RSA-4096 key pair.
     */
    public static KeyPair generateRsaKeyPair() {
        try {
            final KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(RSA_KEY_SIZE, RNG);
            return generator.generateKeyPair();
        }

        catch (final NoSuchAlgorithmException e) {
            // RSA is a standard algorithm required to be present in every JDK/Android.
            throw new IllegalStateException("RSA KeyPairGenerator not available", e);
        }
    }

    /**
     * Generates a fresh random 256-bit AES session key.
     *
     * @return A newly generated AES-256 secret key.
     */
    public static SecretKey generateAesKey() {
        try {
            final KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(AES_KEY_SIZE, RNG);
            return generator.generateKey();
        }

        catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("AES KeyGenerator not available", e);
        }
    }

    /**
     * Generates a fresh random 12-byte GCM IV.
     *
     * @return A newly generated 12-byte IV.
     */
    public static byte[] generateIv() {
        final byte[] iv = new byte[GCM_IV_SIZE];
        RNG.nextBytes(iv);
        return iv;
    }

    /**
     * Encrypts plaintext with AES-256-GCM. A fresh random IV is generated and
     * prepended to the ciphertext (the GCM tag is appended by the cipher).
     * The result is base64-encoded as a single string.
     *
     * @param key The AES session key.
     * @param plaintext The UTF-8 plaintext to encrypt.
     * @return Base64 of {@code IV || ciphertext || tag}.
     */
    public static String encryptAesGcm(final SecretKey key, final String plaintext) {
        Validate.notNull(key, "AES key can not be null");
        Validate.notNull(plaintext, "Plaintext can not be null");

        try {
            final byte[] iv = generateIv();
            final Cipher cipher = Cipher.getInstance(AES_GCM_CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            final byte[] ciphertext = cipher.doFinal(plaintext.getBytes("UTF-8"));

            final byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return base64Encode(combined);
        }

        catch (final Exception e) {
            throw new CryptoException("Failed to encrypt with AES-GCM", e);
        }
    }

    /**
     * Decrypts a base64-encoded {@code IV || ciphertext || tag} blob with AES-256-GCM.
     *
     * @param key The AES session key.
     * @param base64 The base64 blob produced by {@link #encryptAesGcm(SecretKey, String)}.
     * @return The decrypted UTF-8 plaintext.
     */
    public static String decryptAesGcm(final SecretKey key, final String base64) {
        Validate.notNull(key, "AES key can not be null");
        Validate.notNull(base64, "Base64 ciphertext can not be null");

        try {
            final byte[] combined = base64Decode(base64);
            final byte[] iv = new byte[GCM_IV_SIZE];
            final byte[] ciphertext = new byte[combined.length - GCM_IV_SIZE];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_SIZE);
            System.arraycopy(combined, GCM_IV_SIZE, ciphertext, 0, ciphertext.length);

            final Cipher cipher = Cipher.getInstance(AES_GCM_CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), "UTF-8");
        }

        catch (final Exception e) {
            throw new CryptoException("Failed to decrypt with AES-GCM", e);
        }
    }

    /**
     * Wraps (encrypts) an AES session key with the peer's RSA public key using RSA-OAEP.
     *
     * @param peerPublicKey The peer's RSA public key.
     * @param sessionKey The AES session key to wrap.
     * @return Base64-encoded wrapped key.
     */
    public static String wrapSessionKey(final PublicKey peerPublicKey, final SecretKey sessionKey) {
        Validate.notNull(peerPublicKey, "Peer public key can not be null");
        Validate.notNull(sessionKey, "Session key can not be null");

        try {
            final Cipher cipher = Cipher.getInstance(RSA_OAEP_CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, peerPublicKey);
            return base64Encode(cipher.doFinal(sessionKey.getEncoded()));
        }

        catch (final Exception e) {
            throw new CryptoException("Failed to wrap session key", e);
        }
    }

    /**
     * Unwraps (decrypts) an RSA-OAEP-wrapped AES session key with the local private key.
     *
     * @param myPrivateKey The local RSA private key.
     * @param wrapped Base64-encoded wrapped key.
     * @return The recovered AES session key.
     */
    public static SecretKey unwrapSessionKey(final PrivateKey myPrivateKey, final String wrapped) {
        Validate.notNull(myPrivateKey, "Private key can not be null");
        Validate.notNull(wrapped, "Wrapped key can not be null");

        try {
            final Cipher cipher = Cipher.getInstance(RSA_OAEP_CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, myPrivateKey);
            final byte[] keyBytes = cipher.doFinal(base64Decode(wrapped));
            return new SecretKeySpec(keyBytes, "AES");
        }

        catch (final Exception e) {
            throw new CryptoException("Failed to unwrap session key", e);
        }
    }

    /**
     * Signs the given bytes with the local RSA private key (SHA256withRSA).
     *
     * @param myPrivateKey The local RSA private key.
     * @param data The bytes to sign.
     * @return Base64-encoded signature.
     */
    public static String sign(final PrivateKey myPrivateKey, final byte[] data) {
        Validate.notNull(myPrivateKey, "Private key can not be null");
        Validate.notNull(data, "Data can not be null");

        try {
            final Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initSign(myPrivateKey, RNG);
            signature.update(data);
            return base64Encode(signature.sign());
        }

        catch (final Exception e) {
            throw new CryptoException("Failed to sign", e);
        }
    }

    /**
     * Verifies a SHA256withRSA signature against the peer's public key.
     *
     * @param peerPublicKey The peer's RSA public key.
     * @param data The bytes that were signed.
     * @param signatureBase64 The base64-encoded signature.
     * @return True if the signature is valid.
     */
    public static boolean verify(final PublicKey peerPublicKey, final byte[] data, final String signatureBase64) {
        Validate.notNull(peerPublicKey, "Public key can not be null");
        Validate.notNull(data, "Data can not be null");
        Validate.notNull(signatureBase64, "Signature can not be null");

        try {
            final Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initVerify(peerPublicKey);
            signature.update(data);
            return signature.verify(base64Decode(signatureBase64));
        }

        catch (final Exception e) {
            throw new CryptoException("Failed to verify signature", e);
        }
    }

    /**
     * Computes the SHA-256 fingerprint of a public key's DER encoding, returned as
     * colon-separated upper-case hex pairs for display in the trust dialog.
     *
     * @param publicKey The public key to fingerprint.
     * @return The fingerprint, e.g. {@code AB:CD:EF:...}.
     */
    public static String fingerprint(final PublicKey publicKey) {
        Validate.notNull(publicKey, "Public key can not be null");

        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(publicKey.getEncoded());
            return toHex(hash);
        }

        catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Encodes bytes as base64 without line breaks (safe for network messages).
     *
     * <p>Implemented locally rather than via {@code java.util.Base64} so the shared
     * {@code crypto} package compiles on Android API levels where that class is not
     * available.</p>
     *
     * @param bytes The bytes to encode.
     * @return The base64 string.
     */
    public static String base64Encode(final byte[] bytes) {
        Validate.notNull(bytes, "Bytes can not be null");

        final int fullGroups = bytes.length / 3;
        final int remainder = bytes.length % 3;
        final int outputLength = fullGroups * 4 + (remainder == 0 ? 0 : 4);
        final char[] out = new char[outputLength];

        int src = 0;
        int dst = 0;

        for (int i = 0; i < fullGroups; i++) {
            final int b0 = bytes[src++] & 0xff;
            final int b1 = bytes[src++] & 0xff;
            final int b2 = bytes[src++] & 0xff;

            out[dst++] = BASE64_ALPHABET[b0 >> 2];
            out[dst++] = BASE64_ALPHABET[((b0 & 0x03) << 4) | (b1 >> 4)];
            out[dst++] = BASE64_ALPHABET[((b1 & 0x0f) << 2) | (b2 >> 6)];
            out[dst++] = BASE64_ALPHABET[b2 & 0x3f];
        }

        if (remainder == 1) {
            final int b0 = bytes[src++] & 0xff;
            out[dst++] = BASE64_ALPHABET[b0 >> 2];
            out[dst++] = BASE64_ALPHABET[(b0 & 0x03) << 4];
            out[dst++] = '=';
            out[dst] = '=';
        }

        else if (remainder == 2) {
            final int b0 = bytes[src++] & 0xff;
            final int b1 = bytes[src++] & 0xff;
            out[dst++] = BASE64_ALPHABET[b0 >> 2];
            out[dst++] = BASE64_ALPHABET[((b0 & 0x03) << 4) | (b1 >> 4)];
            out[dst++] = BASE64_ALPHABET[(b1 & 0x0f) << 2];
            out[dst] = '=';
        }

        return new String(out);
    }

    /**
     * Decodes a base64 string.
     *
     * @param base64 The base64 string.
     * @return The decoded bytes.
     */
    public static byte[] base64Decode(final String base64) {
        Validate.notNull(base64, "Base64 can not be null");

        final int len = base64.length();

        if (len == 0) {
            return new byte[0];
        }

        // Count padding.
        int pad = 0;
        if (len >= 1 && base64.charAt(len - 1) == '=') {
            pad++;
        }

        if (len >= 2 && base64.charAt(len - 2) == '=') {
            pad++;
        }

        final int outLen = (len / 4) * 3 - pad;
        final byte[] out = new byte[outLen];

        int dst = 0;

        for (int i = 0; i < len; i += 4) {
            final int c0 = BASE64_DECODE[base64.charAt(i) & 0x7f];
            final int c1 = BASE64_DECODE[base64.charAt(i + 1) & 0x7f];
            final int c2 = i + 2 < len ? BASE64_DECODE[base64.charAt(i + 2) & 0x7f] : -1;
            final int c3 = i + 3 < len ? BASE64_DECODE[base64.charAt(i + 3) & 0x7f] : -1;

            final int triplet = (c0 << 18) | (c1 << 12) | ((c2 < 0 ? 0 : c2) << 6) | (c3 < 0 ? 0 : c3);

            if (dst < outLen) {
                out[dst++] = (byte) (triplet >> 16);
            }

            if (dst < outLen) {
                out[dst++] = (byte) (triplet >> 8);
            }

            if (dst < outLen) {
                out[dst++] = (byte) triplet;
            }
        }

        return out;
    }

    private static final char[] BASE64_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

    private static final int[] BASE64_DECODE = buildDecodeTable();

    private static int[] buildDecodeTable() {
        final int[] table = new int[128];

        for (int i = 0; i < 128; i++) {
            table[i] = -1;
        }

        for (int i = 0; i < BASE64_ALPHABET.length; i++) {
            table[BASE64_ALPHABET[i]] = i;
        }

        return table;
    }

    /**
     * Formats the local fingerprint for compact display: upper-case hex grouped
     * in 4-character chunks separated by spaces.
     *
     * @param fingerprint The raw colon-separated fingerprint.
     * @return The grouped fingerprint.
     */
    public static String groupFingerprint(final String fingerprint) {
        Validate.notNull(fingerprint, "Fingerprint can not be null");

        final String compact = fingerprint.replace(":", "");
        final StringBuilder grouped = new StringBuilder();

        for (int i = 0; i < compact.length(); i += 4) {
            if (grouped.length() > 0) {
                grouped.append(" ");
            }

            grouped.append(compact.substring(i, Math.min(i + 4, compact.length())));
        }

        return grouped.toString();
    }

    private static String toHex(final byte[] bytes) {
        final StringBuilder sb = new StringBuilder();

        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) {
                sb.append(":");
            }

            sb.append(String.format("%02X", bytes[i]));
        }

        return sb.toString();
    }
}
