package com.groceryecom.platform.idempotency;

import java.time.Duration;
import java.util.Optional;

/**
 * Storage behind {@link IdempotencyGuard}. Separated so the guard can be unit-tested
 * without Redis, and so the backing store can change without touching callers.
 */
public interface IdempotencyStore {

    /**
     * Claims a key for this caller.
     *
     * @return true when the key was free and is now claimed; false when another request
     *         already claimed it
     */
    boolean claim(String key, Duration ttl);

    /** The stored result for a key, empty while a request holds the claim but has not finished. */
    Optional<String> result(String key);

    /** Records the result of the completed operation, so a retry returns the same answer. */
    void storeResult(String key, String resultJson, Duration ttl);

    /** Releases a claim whose operation failed, so the caller may retry. */
    void release(String key);
}
