package com.groceryecom.platform.ratelimit;

import com.groceryecom.platform.ratelimit.RateLimitProperties.Policy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Counts attempts in this JVM only. Selected with
 * {@code app.security.rate-limit.store=memory}.
 *
 * <p>For tests and for running the app on its own without Redis. Do not use it with more
 * than one instance: each instance would allow the full limit, multiplying the real one.
 */
@Component
@ConditionalOnProperty(name = "app.security.rate-limit.store", havingValue = "memory")
class InMemoryRateLimiter implements RateLimiter {

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    InMemoryRateLimiter() {
        this(Clock.systemUTC());
    }

    InMemoryRateLimiter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Decision check(String key, Policy policy) {
        Instant now = clock.instant();
        Window window = windows.compute(key, (ignored, current) ->
                current == null || !current.expiresAt().isAfter(now)
                        ? new Window(1, now.plus(policy.window()))
                        : new Window(current.attempts() + 1, current.expiresAt()));

        if (window.attempts() > policy.limit()) {
            return Decision.block(java.time.Duration.between(now, window.expiresAt()).toSeconds());
        }
        return Decision.allow();
    }

    private record Window(int attempts, Instant expiresAt) {
    }
}
