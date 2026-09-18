package com.groceryecom.modules.billing.api.dto;

import com.groceryecom.modules.billing.domain.SubscriptionPayment;
import com.groceryecom.modules.billing.domain.SubscriptionPayment.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A charge as the admin console lists it.
 *
 * <p>The work queue behind bank transfers: these are the rows an admin confirms with
 * {@code POST /v1/billing/payments/{reference}/confirm}, so the reference is here and is
 * the thing to copy.
 */
public record AdminPaymentResponse(
        UUID id,
        UUID vendorId,
        String reference,
        String provider,
        long amountMinor,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String failureReason,
        Instant paidAt,
        Instant createdAt) {

    public static AdminPaymentResponse of(SubscriptionPayment payment) {
        return new AdminPaymentResponse(
                payment.getPublicId(),
                payment.getSubscription().getVendorPublicId(),
                payment.getProviderReference(),
                payment.getProvider(),
                payment.getAmountMinor(),
                payment.amount().toDecimal(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getFailureReason(),
                payment.getPaidAt(),
                payment.getCreatedAt());
    }
}
