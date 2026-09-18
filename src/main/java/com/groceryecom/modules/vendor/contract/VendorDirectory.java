package com.groceryecom.modules.vendor.contract;

import java.util.Optional;
import java.util.UUID;

/**
 * What other modules may ask about a store. The two questions every vendor-scoped
 * feature has to answer before it does anything:
 *
 * <ol>
 *   <li>may this store trade at all? ({@link #isSellable})</li>
 *   <li>may this user act for it, and as what? ({@link #membershipOf})</li>
 * </ol>
 *
 * <p>Deliberately narrow: modules get answers, not the vendor entity, so the vendor
 * module stays free to change its table without breaking catalog, order or payment.
 */
public interface VendorDirectory {

    /** True only for an approved store; false for pending, rejected, suspended or unknown. */
    boolean isSellable(UUID vendorId);

    /** The user's role in that store, or empty when they have no business with it. */
    Optional<VendorMemberRole> membershipOf(UUID userId, UUID vendorId);
}
