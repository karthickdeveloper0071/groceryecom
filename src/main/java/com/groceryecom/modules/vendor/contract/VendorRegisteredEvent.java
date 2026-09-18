package com.groceryecom.modules.vendor.contract;

import java.util.UUID;

/**
 * A store applied to sell on the platform and is waiting for a decision.
 *
 * <p>Published in the same transaction as the insert, through the outbox, so a
 * listener that fails does not undo the application.
 *
 * @param vendorId    the store's public id
 * @param ownerUserId the account that applied
 * @param displayName what customers would see
 */
public record VendorRegisteredEvent(UUID vendorId, UUID ownerUserId, String displayName) {
}
