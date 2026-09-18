package com.groceryecom.platform.idempotency;

import com.groceryecom.shared.exception.ConflictException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Retry behaviour, which is what this class exists for: a client that sends the same
 * request twice must be charged once.
 */
class IdempotencyGuardTest {

    private final InMemoryStore store = new InMemoryStore();
    private final IdempotencyGuard guard = new IdempotencyGuard(store, JsonMapper.builder().build());

    record Receipt(String orderId, long amountMinor) {
    }

    @Test
    void runsTheOperationOnceAndReplaysTheStoredResultAfterwards() {
        AtomicInteger runs = new AtomicInteger();

        Receipt first = guard.execute("place-order", "key-1", Receipt.class,
                () -> newReceipt(runs));
        Receipt retry = guard.execute("place-order", "key-1", Receipt.class,
                () -> newReceipt(runs));

        assertThat(runs).hasValue(1);
        assertThat(retry).isEqualTo(first);
    }

    @Test
    void aDifferentKeyIsADifferentOperation() {
        AtomicInteger runs = new AtomicInteger();

        guard.execute("place-order", "key-1", Receipt.class, () -> newReceipt(runs));
        guard.execute("place-order", "key-2", Receipt.class, () -> newReceipt(runs));

        assertThat(runs).hasValue(2);
    }

    @Test
    void theSameKeyInAnotherOperationDoesNotCollide() {
        AtomicInteger runs = new AtomicInteger();

        guard.execute("place-order", "key-1", Receipt.class, () -> newReceipt(runs));
        guard.execute("refund", "key-1", Receipt.class, () -> newReceipt(runs));

        assertThat(runs).hasValue(2);
    }

    @Test
    void aSecondRequestWhileTheFirstIsStillRunningIsRejected() {
        store.claim("place-order:key-1", Duration.ofHours(1));

        assertThatThrownBy(() -> guard.execute("place-order", "key-1", Receipt.class, () -> newReceipt(null)))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", "IDEMPOTENT_REQUEST_IN_PROGRESS")
                .hasFieldOrPropertyWithValue("statusCode", 409);
    }

    @Test
    void aFailedAttemptDoesNotBlockTheRetryThatFixesIt() {
        assertThatThrownBy(() -> guard.execute("place-order", "key-1", Receipt.class, () -> {
            throw new IllegalStateException("payment gateway timed out");
        })).isInstanceOf(IllegalStateException.class);

        AtomicInteger runs = new AtomicInteger();
        Receipt afterRetry = guard.execute("place-order", "key-1", Receipt.class, () -> newReceipt(runs));

        assertThat(runs).hasValue(1);
        assertThat(afterRetry.orderId()).isEqualTo("order-1");
    }

    private static Receipt newReceipt(AtomicInteger runs) {
        int run = runs == null ? 0 : runs.incrementAndGet();
        return new Receipt("order-" + run, 4999L);
    }

    /** Mirrors the Redis implementation's semantics without needing Redis. */
    private static final class InMemoryStore implements IdempotencyStore {

        private static final String CLAIMED = "claimed";
        private final Map<String, String> values = new HashMap<>();

        @Override
        public boolean claim(String key, Duration ttl) {
            return values.putIfAbsent(key, CLAIMED) == null;
        }

        @Override
        public Optional<String> result(String key) {
            String value = values.get(key);
            return value == null || CLAIMED.equals(value) ? Optional.empty() : Optional.of(value);
        }

        @Override
        public void storeResult(String key, String resultJson, Duration ttl) {
            values.put(key, resultJson);
        }

        @Override
        public void release(String key) {
            values.remove(key);
        }
    }
}
