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
import com.groceryecom.shared.exception.ConflictException;
import com.groceryecom.shared.exception.NotFoundException;
import com.groceryecom.shared.money.Money;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
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
    private final PaymentGatewaySelector gateways;
    private final VendorPlanState vendorPlanState;
    private final BillingProperties properties;
    private final ApplicationEventPublisher events;
    private final AuditLog auditLog;

    SubscribeToPlanService(BillingScope billingScope, PlanRepository plans,
                           VendorSubscriptionRepository subscriptions, SubscriptionPaymentRepository payments,
                           PaymentGatewaySelector gateways, VendorPlanState vendorPlanState,
                           BillingProperties properties, ApplicationEventPublisher events, AuditLog auditLog) {
        this.billingScope = billingScope;
        this.plans = plans;
        this.subscriptions = subscriptions;
        this.payments = payments;
        this.gateways = gateways;
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
        // Chosen per payment, from the credentials an admin installed: Razorpay once keys
        // are in, bank transfer until then or if it is switched off
        PaymentGateway gateway = gateways.current();
        Money price = plan.price();

        // The platform generates its own reference, so a charge is identifiable before any
        // provider has seen it. A gateway that issues its own id (Razorpay returns an
        // order id) wins: that is what its webhook will quote, so that is what the payment
        // row is keyed by.
        String reference = "%s-%s".formatted(gateway.provider(), UUID.randomUUID());
        PaymentGateway.PaymentInstruction instruction = gateway.requestPayment(vendorId, price, reference);

        recordCharge(subscription, price, gateway.provider(), instruction.reference());
        log.info("Vendor {} owes {} for the {} plan via {}, reference {}", vendorId, price, plan.getCode(),
                gateway.provider(), instruction.reference());

        return new CheckoutResponse.Payment(instruction.reference(), price.amountMinor(), price.toDecimal(),
                price.currency().getCurrencyCode(), gateway.provider(), instruction.instructions(),
                instruction.redirectUrl(), instruction.publicKey());
    }

    /**
     * Stores the charge, unless this reference is already on file.
     *
     * <p>A gateway can hand back a reference it has issued before: Razorpay returns the
     * existing order for a receipt it has already seen, which is what happens when a
     * vendor opens the checkout, closes it, and opens it again. That is the same charge,
     * not a second one, so the row is reused and the vendor is not billed twice.
     *
     * <p>The same reference against a different store would mean the gateway or this code
     * has confused two charges. That is a conflict worth refusing loudly, and never a
     * silent overwrite.
     */
    private void recordCharge(VendorSubscription subscription, Money price, String provider, String reference) {
        Optional<SubscriptionPayment> existing = payments.findByProviderReference(reference);

        if (existing.isPresent()) {
            if (!existing.get().getSubscription().getId().equals(subscription.getId())) {
                log.warn("Payment reference {} already belongs to another subscription", reference);
                throw new ConflictException("This payment reference is already in use",
                        "PAYMENT_REFERENCE_IN_USE");
            }
            log.info("Reusing the open charge {} instead of creating a second one", reference);
            return;
        }
        payments.save(SubscriptionPayment.pending(subscription, price, provider, reference));
    }
}
