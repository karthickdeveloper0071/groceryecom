package com.groceryecom.modules.vendor.contract;

import java.time.Instant;
import java.util.UUID;

/**
 * How the billing module tells the vendor module about a store's licence.
 *
 * <p>The dependency runs one way on purpose: billing knows about vendors, vendors know
 * nothing about billing. The vendor module keeps a two-field copy of the answer
 * ({@code subscription_active}, {@code plan_expires_at}) so the storefront can decide
 * whether to show a store without reading another module's tables, and so the two
 * modules never form a cycle. ADR-0015 records the trade-off, which is that the copy
 * can drift if a call is lost; the expiry job re-asserts it on every run.
 */
public interface VendorPlanState {

    /**
     * The store is licensed until {@code expiresAt}.
     *
     * <p>A store that was still waiting for approval starts trading here: paying for a
     * plan is the approval. A rejected or suspended store stays closed - an admin
     * decision is not overturned by a payment.
     */
    void planActivated(UUID vendorId, Instant expiresAt);

    /** The licence has run out, grace days included. The store leaves the storefront. */
    void planExpired(UUID vendorId, Instant expiredAt);
}
