package com.groceryecom.platform.security.token;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Redis-backed {@link TokenRegistry}: every app instance sees the same revocations, and
 * entries expire on their own when the token they describe would have expired.
 *
 * <p><b>Fails open.</b> If Redis is unreachable the request is allowed: refusing every
 * request would turn a cache outage into a full outage, and the exposure is bounded by the
 * access token lifetime. Refresh tokens are unaffected by this, because they are validated
 * against the database.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.security.tokens.store", havingValue = "redis", matchIfMissing = true)
class RedisTokenRegistry implements TokenRegistry {

    private static final String REVOKED_ACCESS_KEY = "token:revoked:";
    private static final String REVOKED_SESSION_KEY = "token:session-revoked:";
    private static final String CUTOFF_KEY = "token:cutoff:";
    private static final String MARKER = "revoked";

    private final StringRedisTemplate redis;

    RedisTokenRegistry(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void revokeAccessToken(UUID userId, String tokenId, Duration remainingLifetime) {
        if (remainingLifetime.isNegative() || remainingLifetime.isZero()) {
            return;
        }
        redis.opsForValue().set(REVOKED_ACCESS_KEY + userId + ":" + tokenId, MARKER, remainingLifetime);
    }

    @Override
    public void revokeSession(UUID sessionId, Duration ttl) {
        redis.opsForValue().set(REVOKED_SESSION_KEY + sessionId, MARKER, ttl);
    }

    @Override
    public void revokeAllTokens(UUID userId, Instant cutoff, Duration ttl) {
        redis.opsForValue().set(CUTOFF_KEY + userId, Long.toString(cutoff.toEpochMilli()), ttl);
        log.info("Revoked all tokens for user {} issued before {}", userId, cutoff);
    }

    @Override
    public boolean isRevoked(UUID userId, String tokenId, UUID sessionId, Instant issuedAt) {
        try {
            if (Boolean.TRUE.equals(redis.hasKey(REVOKED_ACCESS_KEY + userId + ":" + tokenId))) {
                return true;
            }
            if (sessionId != null && Boolean.TRUE.equals(redis.hasKey(REVOKED_SESSION_KEY + sessionId))) {
                return true;
            }
            String cutoff = redis.opsForValue().get(CUTOFF_KEY + userId);
            return cutoff != null && issuedAt.toEpochMilli() < Long.parseLong(cutoff);
        } catch (DataAccessException e) {
            log.warn("Cannot check token revocation, allowing request: {}", e.getMessage());
            return false;
        }
    }
}
