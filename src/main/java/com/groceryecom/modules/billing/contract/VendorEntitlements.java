package com.groceryecom.modules.billing.contract;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * What a store's licence allows, for the modules that have to enforce it.
 *
 * <p>Catalog asks before accepting another product, vendor staff management before
 * adding another person, and order before taking money on the platform's behalf. None
 * of them reads the billing tables or knows what a plan costs; they ask this.
 *
 * <p>{@link #requireTrading} is the one to call in a write path: it throws the 402 that
 * tells the vendor their plan expired, in the same words everywhere, instead of each
 * module inventing its own message.
 */
public interface VendorEntitlements {

    /**
     * What the store may do today, or empty if it never bought a plan.
     *
     * @param vendorId the store's public id
     */
    Optional<Entitlement> of(UUID vendorId);

    /**
     * Passes only while the store's licence permits trading.
     *
     * @throws com.groceryecom.shared.exception.PaymentRequiredException 402, with the
     *         date the plan ended, when it does not
     */
    void requireTrading(UUID vendorId);

    /**
     * A store's licence, flattened to the facts another module needs.
     *
     * @param trading             may the store sell right now
     * @param status              why, in one word, for a message to the vendor
     * @param planCode            STARTER, GROWTH, SCALE
     * @param periodEnd           when the paid period ends
     * @param accessEndsAt        when trading actually stops: the period end plus grace
     * @param maxProducts         catalogue limit, null for unlimited
     * @param maxStaff            staff limit, null for unlimited
     * @param commissionBasisPoints the platform's cut per order: 250 = 2.5%
     */
    record Entitlement(boolean trading, SubscriptionStatus status, String planCode,
                       Instant periodEnd, Instant accessEndsAt,
                       Integer maxProducts, Integer maxStaff, int commissionBasisPoints) {
    }
}
