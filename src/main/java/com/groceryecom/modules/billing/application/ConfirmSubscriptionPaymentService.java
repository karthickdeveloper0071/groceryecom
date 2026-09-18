package com.groceryecom.modules.billing.application;

import com.groceryecom.modules.billing.api.dto.SubscriptionResponse;
import com.groceryecom.modules.billing.contract.SubscriptionEvents;
import com.groceryecom.modules.billing.domain.SubscriptionPayment;
import com.groceryecom.modules.billing.domain.SubscriptionPaymentRepository;
import com.groceryecom.modules.billing.domain.VendorSubscription;
import com.groceryecom.modules.billing.domain.VendorSubscriptionRepository;
import com.groceryecom.modules.billing.mapper.BillingMapper;
import com.groceryecom.modules.vendor.contract.VendorPlanState;
import com.groceryecom.platform.audit.AuditLog;
import com.groceryecom.platform.security.AuthenticatedUser;
import com.groceryecom.shared.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * The money arrived: the licence starts or is extended, and the store starts selling.
 *
 * <p>This is the one method that turns a payment into permission, and everything about
 * it is written to be called more than once with the same reference. An admin clicks
 * twice; a gateway retries a webhook it thinks failed; a network drops the response
 * after the commit. Charging a period for each of those would be the platform stealing
 * from its vendors, so settlement is decided by the payment's own status: the first call
 * extends the period, the rest return the same answer and change nothing.
 *
 * <p>When a real gateway is wired in, its webhook endpoint calls this same method. The
 * only thing that changes is who is allowed to call it and how the caller is
 * authenticated - not what it does.
 */
@Slf4j
@Service
public class ConfirmSubscriptionPaymentService {

    private final SubscriptionPaymentRepository payments;
    private final VendorSubscriptionRepository subscriptions;
    private final VendorPlanState vendorPlanState;
    private final BillingProperties properties;
    private final ApplicationEventPublisher events;
    private final AuditLog auditLog;

    ConfirmSubscriptionPaymentService(SubscriptionPaymentRepository payments,
                                      VendorSubscriptionRepository subscriptions,
                                      VendorPlanState vendorPlanState, BillingProperties properties,
                                      ApplicationEventPublisher events, AuditLog auditLog) {
        this.payments = payments;
        this.subscriptions = subscriptions;
        this.vendorPlanState = vendorPlanState;
        this.properties = properties;
        this.events = events;
        this.auditLog = auditLog;
    }

    /**
     * @param confirmedBy the admin confirming a transfer, or null when a gateway webhook
     *                    settles it
     */
    @Transactional
    public SubscriptionResponse execute(AuthenticatedUser confirmedBy, String providerReference) {
        SubscriptionPayment payment = payments.findByProviderReference(providerReference)
                .orElseThrow(() -> new NotFoundException("Payment", providerReference));

        VendorSubscription subscription = payment.getSubscription();
        Instant now = Instant.now();

        if (!payment.succeed(now)) {
            // Already settled: say the same thing again rather than charging for another period
            log.info("Payment {} was already {}; nothing to do", providerReference, payment.getStatus());
            return BillingMapper.toResponse(subscription, now);
        }

        subscription.paymentReceived(now, properties.gracePeriod());
        subscriptions.save(subscription);

        UUID vendorId = subscription.getVendorPublicId();
        // Opens a store that was still waiting for approval: paying is the approval
        vendorPlanState.planActivated(vendorId, subscription.accessEndsAt());

        auditLog.subscriptionPaymentConfirmed(confirmedBy == null ? null : confirmedBy.id(), vendorId,
                payment.amount().toString(), providerReference);
        events.publishEvent(new SubscriptionEvents.SubscriptionActivated(
                vendorId, subscription.getPlan().getCode(), subscription.getCurrentPeriodEnd(),
                subscription.accessEndsAt(), false));

        log.info("Vendor {} is licensed until {} after payment {}", vendorId,
                subscription.getCurrentPeriodEnd(), providerReference);

        return BillingMapper.toResponse(subscription, now);
    }
}
