package com.groceryecom.modules.billing.contract;

import java.time.Instant;
import java.util.UUID;

/**
 * What billing tells the rest of the platform. Written to the outbox in the same
 * transaction as the change, so a listener that fails cannot undo a payment.
 *
 * <p>Notification will turn these into emails: a receipt, a warning a week before the
 * period ends, and the message that says the store has stopped selling.
 */
public final class SubscriptionEvents {

    private SubscriptionEvents() {
    }

    /**
     * A store's licence started or was extended. Its trial counts.
     *
     * @param accessEndsAt when trading stops unless it is renewed, grace included
     */
    public record SubscriptionActivated(UUID vendorId, String planCode, Instant periodEnd,
                                        Instant accessEndsAt, boolean trial) {
    }

    /** The paid period ended without payment. The store is trading on grace days now. */
    public record SubscriptionPastDue(UUID vendorId, String planCode, Instant accessEndsAt) {
    }

    /** Grace ran out. The store has left the storefront. */
    public record SubscriptionExpired(UUID vendorId, String planCode, Instant expiredAt) {
    }

    /** The vendor ended the licence themselves. */
    public record SubscriptionCancelled(UUID vendorId, String planCode, Instant endsAt) {
    }
}
