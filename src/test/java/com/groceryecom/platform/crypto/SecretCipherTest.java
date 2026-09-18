package com.groceryecom.platform.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The encryption that stands between a database dump and a live payment key.
 */
class SecretCipherTest {

    private static final String KEY = "a-master-key-of-at-least-thirty-two-bytes-long";
    private static final String SECRET = "rzp_live_secret_value";

    private final SecretCipher cipher = new SecretCipher(new CryptoProperties(KEY));

    @Test
    void whatIsEncryptedCanBeReadBack() {
        String encrypted = cipher.encrypt(SECRET);

        assertThat(encrypted).doesNotContain(SECRET);
        assertThat(cipher.decrypt(encrypted)).isEqualTo(SECRET);
    }

    /**
     * A fresh nonce per encryption. Identical ciphertexts for identical inputs would tell
     * anybody reading the table which two vendors use the same key.
     */
    @Test
    void encryptingTheSameSecretTwiceGivesDifferentCiphertext() {
        assertThat(cipher.encrypt(SECRET)).isNotEqualTo(cipher.encrypt(SECRET));
    }

    /**
     * GCM authenticates as well as encrypts, so an altered row fails loudly instead of
     * decrypting to a different key and sending payments somewhere unexpected.
     */
    @Test
    void anAlteredCiphertextIsRefusedRatherThanDecryptedToSomethingElse() {
        String encrypted = cipher.encrypt(SECRET);
        String tampered = encrypted.substring(0, encrypted.length() - 2)
                + (encrypted.endsWith("A") ? "B=" : "A=");

        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(SecretCipher.SecretCipherException.class);
    }

    /** The database and the key that protects it never travel together. */
    @Test
    void anotherMasterKeyCannotReadTheseSecrets() {
        String encrypted = cipher.encrypt(SECRET);
        SecretCipher otherEnvironment =
                new SecretCipher(new CryptoProperties("a-different-master-key-also-thirty-two-plus"));

        assertThatThrownBy(() -> otherEnvironment.decrypt(encrypted))
                .isInstanceOf(SecretCipher.SecretCipherException.class)
                .hasMessageContaining("Enter it again");
    }

    @Test
    void aMasterKeyTooShortForAes256StopsTheApplication() {
        assertThatThrownBy(() -> new SecretCipher(new CryptoProperties("too-short")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void aMissingMasterKeyStopsTheApplication() {
        assertThatThrownBy(() -> new CryptoProperties(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SECRETS_MASTER_KEY");
    }
}
