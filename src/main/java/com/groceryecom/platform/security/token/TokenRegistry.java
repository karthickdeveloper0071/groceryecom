package com.groceryecom.platform.security.token;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * The fast path for refusing access tokens.
 *
 * <p>A signed JWT is valid until it expires, which is what makes it cheap: no lookup per
 * request. Logout, "log out everywhere" and a password change all need a token to stop
 * working before that, so this registry holds just enough state to refuse one, keyed so
 * that entries expire by themselves.
 *
 * <p>Sessions and refresh tokens live in the database ({@code user_sessions}), which is the
 * source of truth. This registry is a cache in front of the access-token check, because
 * checking the database on every request would cost a query per request. Losing it means
 * revoked access tokens work until they expire (at most the access token TTL); it can
 * never resurrect a revoked session, because refreshing reads the database.
 */
public interface TokenRegistry {

    /** Refuses one access token until it would have expired anyway (logout of this device). */
    void revokeAccessToken(UUID userId, String tokenId, Duration remainingLifetime);

    /** Refuses every access token issued for one session (that device is signed out). */
    void revokeSession(UUID sessionId, Duration ttl);

    /**
     * Refuses every token of this user issued before {@code cutoff}, for a password change
     * or "log out everywhere".
     *
     * <p>Comparison uses the {@code iat_ms} claim, so a token issued milliseconds after the
     * cut-off (the one from logging straight back in) still works.
     */
    void revokeAllTokens(UUID userId, Instant cutoff, Duration ttl);

    /**
     * @param issuedAt the token's issue time, from its {@code iat_ms} claim
     * @return true when this token must no longer be accepted
     */
    boolean isRevoked(UUID userId, String tokenId, UUID sessionId, Instant issuedAt);
}
