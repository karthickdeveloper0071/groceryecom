package com.groceryecom.modules.identity.contract;

/**
 * Platform-wide account roles.
 * Which store a vendor owner or staff member acts for is decided by vendor
 * membership in the vendor module, not by the role alone.
 */
public enum Role {
    CUSTOMER,
    VENDOR_OWNER,
    VENDOR_STAFF,
    ADMIN
}
