package com.groceryecom.platform.ratelimit;

import com.groceryecom.platform.ratelimit.RateLimitProperties.Policy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Counts attempts in Redis, so the limit is shared by every app instance.
 *
 * <p>A fixed window per key: the first attempt creates the counter with the window as its
 * time to live, and the key disappears on its own. A caller can therefore send up to twice
 * the limit across a window boundary; that is an accepted trade for something this simple
 * and cheap. A sliding window would need a sorted set per key and more memory than the
 * protection is worth here.
 *
 * <p><b>When Redis is down</b> the request is allowed. A rate limiter that cannot count
 * must not become an outage of login for every customer; the risk it covers (guessing
 * passwords) is smaller than the risk of refusing all traffic. The failure is logged so
 * it is visible.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.security.rate-limit.store", havingValue = "redis", matchIfMissing = true)
class RedisRateLimiter implements RateLimiter {

    private static final String KEY_PREFIX = "rate-limit:";

    private final StringRedisTemplate redis;

    RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Decision check(String key, Policy policy) {
        String redisKey = KEY_PREFIX + key;
        try {
            Long attempts = redis.opsForValue().increment(redisKey);
            if (attempts == null) {
                return Decision.allow();
            }
            if (attempts == 1L) {
                redis.expire(redisKey, policy.window());
            }
            if (attempts > policy.limit()) {
                return Decision.block(secondsUntilReset(redisKey, policy.window()));
            }
            return Decision.allow();
        } catch (DataAccessException e) {
            log.warn("Rate limiting unavailable, allowing request: {}", e.getMessage());
            return Decision.allow();
        }
    }

    private long secondsUntilReset(String redisKey, Duration window) {
        Long remaining = redis.getExpire(redisKey, TimeUnit.SECONDS);
        return remaining != null && remaining > 0 ? remaining : window.toSeconds();
    }
}
