package com.groceryecom.modules.vendor.contract;

/**
 * What a person may do for one store.
 *
 * <p>Separate from the platform-wide account role: an account is a customer, a vendor
 * person or an admin, while this says <em>which store</em> that person acts for and in
 * what capacity. A user with no membership has no access to any vendor's data.
 */
public enum VendorMemberRole {

    /** Applied for the store; may manage its profile and its staff. */
    OWNER,

    /** Added by an owner to run the store day to day. */
    STAFF
}
