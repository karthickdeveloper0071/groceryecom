package com.groceryecom.modules.billing.application;

import com.groceryecom.modules.billing.api.dto.CheckoutResponse;
import com.groceryecom.modules.billing.api.dto.SubscribeRequest;
import com.groceryecom.modules.billing.contract.SubscriptionEvents;
import com.groceryecom.modules.billing.domain.Plan;
import com.groceryecom.modules.billing.domain.PlanRepository;
import com.groceryecom.modules.billing.domain.SubscriptionPayment;
import com.groceryecom.modules.billing.domain.SubscriptionPaymentRepository;
import com.groceryecom.modules.billing.domain.VendorSubscription;
import com.groceryecom.modules.billing.domain.VendorSubscriptionRepository;
import com.groceryecom.modules.billing.mapper.BillingMapper;
import com.groceryecom.modules.vendor.contract.VendorPlanState;
import com.groceryecom.platform.audit.AuditLog;
import com.groceryecom.platform.security.AuthenticatedUser;
import com.groceryecom.shared.exception.NotFoundException;
import com.groceryecom.shared.money.Money;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * A store chooses a plan, or moves to another one.
 *
 * <p>Two outcomes, and which one happens is the plan's business, not the checkout's:
 * <ul>
 *   <li>a plan with trial days starts the store trading immediately, and asks for
 *       nothing yet. This is what lets a vendor sign up and use the platform the same
 *       minute, with no admin in the loop;</li>
 *   <li>a plan without them creates a charge, and the store starts trading when the
 *       money arrives ({@link ConfirmSubscriptionPaymentService}).</li>
 * </ul>
 *
 * <p>Changing plan keeps the same licence row: the new plan takes effect when the next
 * payment lands, and a store that is mid-period is not switched off for changing its
 * mind.
 */
@Slf4j
@Service
public class SubscribeToPlanService {

    private final BillingScope billingScope;
    private final PlanRepository plans;
    private final VendorSubscriptionRepository subscriptions;
    private final SubscriptionPaymentRepository payments;
    private final PaymentGateway gateway;
    private final VendorPlanState vendorPlanState;
    private final BillingProperties properties;
    private final ApplicationEventPublisher events;
    private final AuditLog auditLog;

    SubscribeToPlanService(BillingScope billingScope, PlanRepository plans,
                           VendorSubscriptionRepository subscriptions, SubscriptionPaymentRepository payments,
                           PaymentGateway gateway, VendorPlanState vendorPlanState,
                           BillingProperties properties, ApplicationEventPublisher events, AuditLog auditLog) {
        this.billingScope = billingScope;
        this.plans = plans;
        this.subscriptions = subscriptions;
        this.payments = payments;
        this.gateway = gateway;
        this.vendorPlanState = vendorPlanState;
        this.properties = properties;
        this.events = events;
        this.auditLog = auditLog;
    }

    @Transactional
    public CheckoutResponse execute(AuthenticatedUser user, UUID vendorId, SubscribeRequest request) {
        billingScope.requireOwner(user, vendorId);

        Plan plan = plans.findByCodeAndIsActiveTrue(request.planCode())
                .orElseThrow(() -> new NotFoundException("Plan", request.planCode()));

        Instant now = Instant.now();
        VendorSubscription subscription = subscriptions.findByVendorPublicId(vendorId)
                .map(existing -> changePlan(existing, plan))
                .orElseGet(() -> VendorSubscription.start(vendorId, plan, now, properties.gracePeriod()));

        VendorSubscription saved = subscriptions.save(subscription);
        auditLog.subscriptionChanged(user.id(), vendorId, plan.getCode(), saved.getStatus().name());

        if (saved.permitsTrading()) {
            // A trial: the store can sell now, and owes nothing yet
            startTrading(saved, plan, true);
            return new CheckoutResponse(BillingMapper.toResponse(saved, now), null);
        }
        return new CheckoutResponse(BillingMapper.toResponse(saved, now), requestPayment(saved, plan, vendorId));
    }

    private VendorSubscription changePlan(VendorSubscription subscription, Plan plan) {
        subscription.switchTo(plan, properties.gracePeriod());
        return subscription;
    }

    private void startTrading(VendorSubscription subscription, Plan plan, boolean trial) {
        // Tells the vendor module the store is licensed, which also opens a store that
        // was waiting for approval. One direction: billing calls vendor, never the reverse.
        vendorPlanState.planActivated(subscription.getVendorPublicId(), subscription.accessEndsAt());

        events.publishEvent(new SubscriptionEvents.SubscriptionActivated(
                subscription.getVendorPublicId(), plan.getCode(),
                subscription.getCurrentPeriodEnd(), subscription.accessEndsAt(), trial));
    }

    private CheckoutResponse.Payment requestPayment(VendorSubscription subscription, Plan plan, UUID vendorId) {
        Money price = plan.price();
        // The platform generates the reference, so a charge is identifiable before any
        // provider has seen it, and confirming one twice is detectable
        String reference = "%s-%s".formatted(gateway.provider(), UUID.randomUUID());

        PaymentGateway.PaymentInstruction instruction = gateway.requestPayment(vendorId, price, reference);
        payments.save(SubscriptionPayment.pending(subscription, price, gateway.provider(), instruction.reference()));

        log.info("Vendor {} owes {} for the {} plan, reference {}", vendorId, price, plan.getCode(),
                instruction.reference());

        return new CheckoutResponse.Payment(instruction.reference(), price.amountMinor(), price.toDecimal(),
                price.currency().getCurrencyCode(), gateway.provider(), instruction.instructions(),
                instruction.redirectUrl());
    }
}
