package com.groceryecom.modules.billing.application;

import com.groceryecom.modules.billing.api.dto.SubscriptionResponse;
import com.groceryecom.modules.billing.contract.SubscriptionEvents;
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
 * A store ends its licence.
 *
 * <p>It keeps selling until the end of the period it already paid for. Switching a shop
 * off the moment it cancels would be keeping the month's money for nothing, and a
 * platform that does that is one vendors warn each other about.
 *
 * <p>Cancelling is not deleting: the row stays, the history stays, and choosing a plan
 * again starts the same licence rather than a second one.
 */
@Slf4j
@Service
public class CancelSubscriptionService {

    private final BillingScope billingScope;
    private final VendorSubscriptionRepository subscriptions;
    private final VendorPlanState vendorPlanState;
    private final ApplicationEventPublisher events;
    private final AuditLog auditLog;

    CancelSubscriptionService(BillingScope billingScope, VendorSubscriptionRepository subscriptions,
                              VendorPlanState vendorPlanState, ApplicationEventPublisher events,
                              AuditLog auditLog) {
        this.billingScope = billingScope;
        this.subscriptions = subscriptions;
        this.vendorPlanState = vendorPlanState;
        this.events = events;
        this.auditLog = auditLog;
    }

    @Transactional
    public SubscriptionResponse execute(AuthenticatedUser user, UUID vendorId) {
        billingScope.requireOwner(user, vendorId);

        VendorSubscription subscription = subscriptions.findByVendorPublicId(vendorId)
                .orElseThrow(() -> new NotFoundException("Subscription", vendorId));

        Instant now = Instant.now();
        subscription.cancel(now);
        subscriptions.save(subscription);

        if (!subscription.permitsTrading()) {
            // Nothing was paid for, so there is nothing left to use: the store closes now
            vendorPlanState.planExpired(vendorId, now);
        }

        auditLog.subscriptionChanged(user.id(), vendorId, subscription.getPlan().getCode(),
                subscription.getStatus().name());
        events.publishEvent(new SubscriptionEvents.SubscriptionCancelled(
                vendorId, subscription.getPlan().getCode(), subscription.getCurrentPeriodEnd()));

        log.info("Vendor {} cancelled its plan; selling until {}", vendorId, subscription.getCurrentPeriodEnd());
        return BillingMapper.toResponse(subscription, now);
    }
}
