package com.groceryecom.platform.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Base64;

/**
 * The master key that protects stored secrets, under {@code app.security.secrets}.
 *
 * <p>Set {@code SECRETS_MASTER_KEY} in every shared environment, from a secrets manager,
 * different per environment. The local default is a published value and protects nothing;
 * it exists so a developer can run the application without ceremony, and the constructor
 * refuses anything too short to be an AES-256 key.
 *
 * <p>Rotating it is not transparent: secrets already stored cannot be read with a new key,
 * and have to be entered again in the admin console. That is written down in
 * docs/engineering/security-standard.md rather than discovered during an incident.
 *
 * @param masterKey Base64 or plain text, at least 32 bytes
 */
@ConfigurationProperties("app.security.secrets")
public record CryptoProperties(String masterKey) {

    private static final int AES_256_BYTES = 32;

    public CryptoProperties {
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalStateException(
                    "app.security.secrets.master-key is required: set SECRETS_MASTER_KEY");
        }
    }

    /** Exactly 32 bytes, derived from the configured value. */
    byte[] masterKeyBytes() {
        byte[] decoded = decode(masterKey);

        if (decoded.length < AES_256_BYTES) {
            throw new IllegalStateException(
                    "app.security.secrets.master-key must be at least %d bytes; generate one with "
                            .formatted(AES_256_BYTES) + "openssl rand -base64 32");
        }
        // Longer keys are truncated rather than rejected: AES-256 takes 32 bytes, and a
        // 64-byte value from a secrets manager is a reasonable thing for someone to paste
        byte[] key = new byte[AES_256_BYTES];
        System.arraycopy(decoded, 0, key, 0, AES_256_BYTES);
        return key;
    }

    private static byte[] decode(String value) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException notBase64) {
            return value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
