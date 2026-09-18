package com.groceryecom.platform.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-for-junit-only-must-be-at-least-sixty-four-bytes-long-for-hs512";
    private static final UUID USER_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-17T10:00:00Z");

    private final JwtTokenProvider provider = providerAt(NOW, SECRET);

    @Test
    void accessTokenCarriesUserIdUsernameAndRoles() {
        String token = provider.createAccessToken(USER_ID, "alice", List.of("CUSTOMER"));

        assertThat(provider.parseAccessToken(token))
                .contains(new AuthenticatedUser(USER_ID, "alice", Set.of("CUSTOMER")));
    }

    @Test
    void refreshTokenCarriesUserId() {
        assertThat(provider.parseRefreshToken(provider.createRefreshToken(USER_ID))).contains(USER_ID);
    }

    @Test
    void tokenTypesCannotBeSwapped() {
        assertThat(provider.parseAccessToken(provider.createRefreshToken(USER_ID))).isEmpty();
        assertThat(provider.parseRefreshToken(provider.createAccessToken(USER_ID, "alice", List.of()))).isEmpty();
    }

    @Test
    void expiredTokenIsRejected() {
        String token = provider.createAccessToken(USER_ID, "alice", List.of("CUSTOMER"));

        assertThat(providerAt(NOW.plus(Duration.ofMinutes(16)), SECRET).parseAccessToken(token)).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String otherSecret = SECRET.replace("test", "evil");
        String forged = providerAt(NOW, otherSecret).createAccessToken(USER_ID, "admin", List.of("ADMIN"));

        assertThat(provider.parseAccessToken(forged)).isEmpty();
    }

    @Test
    void tamperedOrMalformedTokenIsRejected() {
        String token = provider.createAccessToken(USER_ID, "alice", List.of("CUSTOMER"));
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");

        assertThat(provider.parseAccessToken(tampered)).isEmpty();
        assertThat(provider.parseAccessToken("not-a-jwt")).isEmpty();
        assertThat(provider.parseAccessToken("")).isEmpty();
    }

    @Test
    void secretShorterThan64BytesIsRefusedAtStartup() {
        assertThatThrownBy(() -> new JwtProperties("too-short", Duration.ofMinutes(15), Duration.ofDays(7)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 64 bytes");
    }

    private static JwtTokenProvider providerAt(Instant instant, String secret) {
        JwtProperties properties = new JwtProperties(secret, Duration.ofMinutes(15), Duration.ofDays(7));
        return new JwtTokenProvider(properties, Clock.fixed(instant, ZoneOffset.UTC));
    }
}
