package com.groceryecom.modules.billing.contract;

/**
 * The life of a store's licence.
 *
 * <pre>
 *   PENDING_PAYMENT --pay--> ACTIVE --period ends--> PAST_DUE --grace ends--> EXPIRED
 *          |                   ^                        |                        |
 *          +--trial--> TRIALING+                        +--------pay------------>+--> ACTIVE
 *                          |
 *                          +--cancel--> CANCELLED
 * </pre>
 *
 * <p>{@link #PAST_DUE} exists because a shop that stops selling the minute a card
 * expires is a shop that leaves the platform. During grace the store keeps trading and
 * is told, loudly, that payment failed.
 */
public enum SubscriptionStatus {

    /** Chosen a plan, money not received yet. The store cannot trade. */
    PENDING_PAYMENT,

    /** Free trial. Trades exactly like a paid store until the trial ends. */
    TRIALING,

    /** Paid and inside its period. */
    ACTIVE,

    /** The period ended without payment, but the grace days have not. Still trading. */
    PAST_DUE,

    /** Grace ran out. The store leaves the storefront until it pays. */
    EXPIRED,

    /** The vendor ended it, or an admin did. */
    CANCELLED;

    /** Whether a store with this licence may trade. */
    public boolean permitsTrading() {
        return this == TRIALING || this == ACTIVE || this == PAST_DUE;
    }
}
