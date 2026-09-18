package com.groceryecom.platform.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * JWT settings under {@code app.security.jwt}. The application refuses to start
 * with a missing or too-short secret instead of failing on the first login.
 */
@Validated
@ConfigurationProperties("app.security.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl) {

    /** HS512 requires a key of at least 512 bits. */
    static final int MIN_SECRET_BYTES = 64;

    public JwtProperties {
        if (secret != null && secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "app.security.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes for HS512");
        }
    }
}
