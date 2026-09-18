package com.groceryecom.platform.idempotency;

import com.groceryecom.shared.exception.ConflictException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Makes an operation safe to retry: the same {@code Idempotency-Key} runs the work once
 * and returns the same answer afterwards.
 *
 * <p>Clients retry. A phone loses signal after the order was created, a payment webhook
 * is delivered twice, a user double-taps. Without this, a retry charges the card again or
 * creates a second order.
 *
 * <p>Use it in the use cases where a duplicate costs money or confuses a customer:
 * placing an order, taking a payment, issuing a refund, creating a shipment. It is opt-in,
 * so an operation that is naturally safe to repeat does not pay for storage it never needs.
 *
 * <pre>
 * return idempotencyGuard.execute("place-order", request.idempotencyKey(), OrderResponse.class,
 *         () -> placeOrder(request));
 * </pre>
 *
 * <p>Concurrent calls with the same key do not both run: the second gets 409
 * {@code IDEMPOTENT_REQUEST_IN_PROGRESS} and can retry once the first has finished.
 */
@Slf4j
@Component
public class IdempotencyGuard {

    /** Long enough to cover client retries and a webhook redelivery, short enough to expire. */
    private static final Duration RETENTION = Duration.ofHours(24);

    private final IdempotencyStore store;
    private final JsonMapper jsonMapper;

    IdempotencyGuard(IdempotencyStore store, JsonMapper jsonMapper) {
        this.store = store;
        this.jsonMapper = jsonMapper;
    }

    /**
     * @param scope  the operation name, so the same key used for different operations does
     *               not collide (for example "place-order" and "refund")
     * @param key    the caller's Idempotency-Key header value
     * @param type   the result type, needed to read a stored result back
     * @param action the work to run at most once for this key
     */
    public <T> T execute(String scope, String key, Class<T> type, Supplier<T> action) {
        String storageKey = scope + ":" + key;

        Optional<String> stored = store.result(storageKey);
        if (stored.isPresent()) {
            log.info("Replaying stored result for idempotency key scope={}", scope);
            return jsonMapper.readValue(stored.get(), type);
        }

        if (!store.claim(storageKey, RETENTION)) {
            // Either a request is still running, or it finished between the two calls above
            Optional<String> raced = store.result(storageKey);
            if (raced.isPresent()) {
                return jsonMapper.readValue(raced.get(), type);
            }
            throw new ConflictException("A request with this Idempotency-Key is still being processed",
                    "IDEMPOTENT_REQUEST_IN_PROGRESS");
        }

        try {
            T result = action.get();
            store.storeResult(storageKey, jsonMapper.writeValueAsString(result), RETENTION);
            return result;
        } catch (RuntimeException e) {
            // A failed attempt must not block the retry that fixes it
            store.release(storageKey);
            throw e;
        }
    }
}
