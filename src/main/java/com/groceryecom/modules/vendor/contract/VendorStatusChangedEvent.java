package com.groceryecom.modules.vendor.contract;

import java.util.UUID;

/**
 * An admin decided about a store: approved, rejected or suspended it.
 *
 * <p>The module that emails the vendor, and the module that has to hide or restore its
 * products, both react to this rather than polling the vendor table.
 *
 * @param vendorId the store's public id
 * @param status   the status it now has
 * @param reason   why, for a rejection or a suspension; null for an approval
 */
public record VendorStatusChangedEvent(UUID vendorId, VendorStatus status, String reason) {
}
