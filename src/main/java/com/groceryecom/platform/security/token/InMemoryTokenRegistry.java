package com.groceryecom.platform.security.token;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps revocation state in this JVM only. Selected with
 * {@code app.security.tokens.store=memory}.
 *
 * <p>For tests and for running the app on its own without Redis. With more than one
 * instance, a token revoked on one instance would still be accepted by the others, so
 * never use this in a shared environment.
 */
@Component
@ConditionalOnProperty(name = "app.security.tokens.store", havingValue = "memory")
class InMemoryTokenRegistry implements TokenRegistry {

    private final Set<String> revokedAccessTokens = ConcurrentHashMap.newKeySet();
    private final Set<UUID> revokedSessions = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Instant> cutoffs = new ConcurrentHashMap<>();

    @Override
    public void revokeAccessToken(UUID userId, String tokenId, Duration remainingLifetime) {
        revokedAccessTokens.add(userId + ":" + tokenId);
    }

    @Override
    public void revokeSession(UUID sessionId, Duration ttl) {
        revokedSessions.add(sessionId);
    }

    @Override
    public void revokeAllTokens(UUID userId, Instant cutoff, Duration ttl) {
        cutoffs.put(userId, cutoff);
    }

    @Override
    public boolean isRevoked(UUID userId, String tokenId, UUID sessionId, Instant issuedAt) {
        if (revokedAccessTokens.contains(userId + ":" + tokenId)) {
            return true;
        }
        if (sessionId != null && revokedSessions.contains(sessionId)) {
            return true;
        }
        Instant cutoff = cutoffs.get(userId);
        return cutoff != null && issuedAt.isBefore(cutoff);
    }
}
