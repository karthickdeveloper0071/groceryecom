package com.groceryecom.modules.vendor.contract;

/**
 * Where a store is in its life on the platform.
 *
 * <p>Only {@link #APPROVED} may sell. Every other status means a customer must not see
 * the store or its products, which is why {@code VendorDirectory.isSellable} exists
 * rather than each module deciding for itself.
 */
public enum VendorStatus {

    /** Applied, waiting for an admin decision. Visible to its own members only. */
    PENDING,

    /** Trading. The only status that may list products and take orders. */
    APPROVED,

    /** An admin refused the application. Terminal. */
    REJECTED,

    /** Was trading, stopped by an admin. Can be approved again. */
    SUSPENDED;

    public boolean isSellable() {
        return this == APPROVED;
    }
}
