package com.groceryecom.platform.crypto;

import com.groceryecom.shared.exception.ApplicationException;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts the few secrets the platform has to store rather than hash: payment gateway
 * keys today, an outbound API token tomorrow.
 *
 * <p>AES-256-GCM. GCM rather than CBC because it authenticates as well as encrypts: a
 * ciphertext altered in the database fails to decrypt instead of quietly producing a
 * different key. A fresh 12-byte nonce is generated per encryption and stored in front of
 * the ciphertext, because reusing a nonce with the same key breaks GCM completely.
 *
 * <p>The master key comes from the environment, never from the database, so a database
 * dump on its own is useless. Losing it means re-entering the secrets it protected -
 * deliberately, because the alternative is a key that travels with its own ciphertext.
 *
 * <p>This is not a general-purpose crypto utility and must not become one. Passwords are
 * hashed with BCrypt and never encrypted; tokens are signed, not encrypted. If something
 * can be hashed instead of stored, hash it.
 */
@Component
public class SecretCipher {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey masterKey;
    private final SecureRandom random = new SecureRandom();

    SecretCipher(CryptoProperties properties) {
        this.masterKey = new SecretKeySpec(properties.masterKeyBytes(), ALGORITHM);
    }

    /** @return nonce and ciphertext, Base64, safe to store in a text column */
    public String encrypt(String plaintext) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[nonce.length + ciphertext.length];
            System.arraycopy(nonce, 0, combined, 0, nonce.length);
            System.arraycopy(ciphertext, 0, combined, nonce.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            // The message deliberately says nothing about what was being encrypted
            throw new SecretCipherException("Could not encrypt a stored secret");
        }
    }

    /**
     * @throws SecretCipherException when the value was encrypted with a different master
     *         key, or was altered. Both mean the same thing operationally: the secret has
     *         to be entered again.
     */
    public String decrypt(String encoded) {
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, masterKey,
                    new GCMParameterSpec(TAG_BITS, combined, 0, NONCE_BYTES));
            byte[] plaintext = cipher.doFinal(combined, NONCE_BYTES, combined.length - NONCE_BYTES);

            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new SecretCipherException(
                    "A stored secret could not be read. It was encrypted with a different key, "
                            + "or has been altered. Enter it again in the admin console.");
        }
    }

    /** 500: the platform cannot read something it stored, and no client can fix that. */
    public static class SecretCipherException extends ApplicationException {

        SecretCipherException(String message) {
            super(message, "SECRET_UNREADABLE", 500);
        }
    }
}
