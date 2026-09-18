package com.groceryecom.platform.security.token;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Access-token revocation. Refresh tokens are not here: they live in the
 * {@code user_sessions} table, so that revoking survives a Redis restart.
 */
class InMemoryTokenRegistryTest {

    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final UUID PHONE_SESSION = UUID.randomUUID();
    private static final UUID LAPTOP_SESSION = UUID.randomUUID();
    private static final Duration WEEK = Duration.ofDays(7);
    private static final Duration QUARTER_HOUR = Duration.ofMinutes(15);
    private static final Instant NOW = Instant.parse("2026-09-18T10:00:00Z");

    private final TokenRegistry registry = new InMemoryTokenRegistry();

    @Test
    void acceptsATokenNobodyRevoked() {
        assertThat(registry.isRevoked(ALICE, "token-1", PHONE_SESSION, NOW)).isFalse();
    }

    @Test
    void revokingOneTokenLeavesTheOthersAlone() {
        registry.revokeAccessToken(ALICE, "token-1", QUARTER_HOUR);

        assertThat(registry.isRevoked(ALICE, "token-1", PHONE_SESSION, NOW)).isTrue();
        assertThat(registry.isRevoked(ALICE, "token-2", PHONE_SESSION, NOW)).isFalse();
        assertThat(registry.isRevoked(BOB, "token-1", PHONE_SESSION, NOW)).isFalse();
    }

    /** Signing out one device must not sign out the others. */
    @Test
    void revokingOneSessionRefusesOnlyThatSessionsTokens() {
        registry.revokeSession(PHONE_SESSION, QUARTER_HOUR);

        assertThat(registry.isRevoked(ALICE, "token-1", PHONE_SESSION, NOW)).isTrue();
        assertThat(registry.isRevoked(ALICE, "token-2", LAPTOP_SESSION, NOW)).isFalse();
    }

    @Test
    void revokingEverythingRefusesTokensIssuedBeforeTheCutOffOnly() {
        registry.revokeAllTokens(ALICE, NOW, WEEK);

        assertThat(registry.isRevoked(ALICE, "old", PHONE_SESSION, NOW.minusSeconds(1))).isTrue();
        // A token issued after logging out everywhere is a fresh login, and stays valid
        assertThat(registry.isRevoked(ALICE, "new", PHONE_SESSION, NOW.plusSeconds(1))).isFalse();
        assertThat(registry.isRevoked(BOB, "other", LAPTOP_SESSION, NOW.minusSeconds(1))).isFalse();
    }

    /**
     * Revocation is millisecond-accurate, which is why tokens carry an {@code iat_ms}
     * claim: the standard {@code iat} is only accurate to the second, and a logout must
     * refuse the token issued 200 ms before it while accepting the one from logging back
     * in 100 ms after.
     */
    @Test
    void theCutOffIsAccurateToTheMillisecond() {
        registry.revokeAllTokens(ALICE, NOW.plusMillis(800), WEEK);

        assertThat(registry.isRevoked(ALICE, "before", PHONE_SESSION, NOW.plusMillis(600))).isTrue();
        assertThat(registry.isRevoked(ALICE, "after", PHONE_SESSION, NOW.plusMillis(900))).isFalse();
    }

    @Test
    void aTokenWithNoSessionClaimIsStillCheckedAgainstTheOtherRules() {
        registry.revokeAccessToken(ALICE, "token-1", QUARTER_HOUR);

        assertThat(registry.isRevoked(ALICE, "token-1", null, NOW)).isTrue();
        assertThat(registry.isRevoked(ALICE, "token-2", null, NOW)).isFalse();
    }
}
