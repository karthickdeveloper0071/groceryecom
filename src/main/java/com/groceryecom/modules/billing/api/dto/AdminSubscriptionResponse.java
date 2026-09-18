package com.groceryecom.modules.billing.api.dto;

import com.groceryecom.modules.billing.contract.SubscriptionStatus;
import com.groceryecom.modules.billing.domain.VendorSubscription;

import java.time.Instant;
import java.util.UUID;

/**
 * A licence as the admin console lists it.
 *
 * <p>Flatter and smaller than the vendor's own view: an admin scanning a hundred rows
 * needs the store, the plan, the state and the dates, not the sentence written for a
 * shopkeeper.
 */
public record AdminSubscriptionResponse(
        UUID id,
        UUID vendorId,
        String planCode,
        String planName,
        SubscriptionStatus status,
        boolean trading,
        Instant currentPeriodEnd,
        Instant accessEndsAt,
        Instant cancelledAt,
        Instant createdAt) {

    public static AdminSubscriptionResponse of(VendorSubscription subscription) {
        return new AdminSubscriptionResponse(
                subscription.getPublicId(),
                subscription.getVendorPublicId(),
                subscription.getPlan().getCode(),
                subscription.getPlan().getName(),
                subscription.getStatus(),
                subscription.permitsTrading(),
                subscription.getCurrentPeriodEnd(),
                subscription.accessEndsAt(),
                subscription.getCancelledAt(),
                subscription.getCreatedAt());
    }
}
