package com.groceryecom.platform.ratelimit;

import com.groceryecom.platform.ratelimit.RateLimitProperties.Policy;

/**
 * Counts attempts per key and says whether one more is allowed.
 *
 * <p>Implementations must be safe to call from every app instance at once: the count
 * belongs to the platform, not to one JVM.
 */
public interface RateLimiter {

    /**
     * Records an attempt against {@code key} and reports whether it may proceed.
     *
     * @param key    what is being limited, for example {@code login:ip:203.0.113.7}
     * @param policy how many attempts are allowed in which window
     */
    Decision check(String key, Policy policy);

    /**
     * @param allowed           false when the caller has exhausted the window
     * @param retryAfterSeconds how long until the window resets; 0 when allowed
     */
    record Decision(boolean allowed, long retryAfterSeconds) {

        public static Decision allow() {
            return new Decision(true, 0);
        }

        /** Retry-After is never 0, so a client always waits at least a second. */
        public static Decision block(long retryAfterSeconds) {
            return new Decision(false, Math.max(retryAfterSeconds, 1));
        }
    }
}
