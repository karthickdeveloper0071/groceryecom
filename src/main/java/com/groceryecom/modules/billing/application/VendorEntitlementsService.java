package com.groceryecom.modules.billing.application;

import com.groceryecom.modules.billing.contract.VendorEntitlements;
import com.groceryecom.modules.billing.domain.Plan;
import com.groceryecom.modules.billing.domain.VendorSubscription;
import com.groceryecom.modules.billing.domain.VendorSubscriptionRepository;
import com.groceryecom.shared.exception.PaymentRequiredException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

/**
 * What billing tells the rest of the platform about a store's licence.
 *
 * <p>This is the enforcement point for every module that comes next: catalog before
 * accepting another product, vendor staff before adding a person, order before taking
 * money. They call {@link #requireTrading} and get either silence or a 402 that already
 * says the right thing to the vendor.
 *
 * <p>Two error codes, because they are two different situations for the client: a store
 * that never chose a plan needs the price list, and one whose plan ran out needs a renew
 * button with a date on it.
 */
@Service
class VendorEntitlementsService implements VendorEntitlements {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d MMMM yyyy").withZone(ZoneOffset.UTC);

    private final VendorSubscriptionRepository subscriptions;

    VendorEntitlementsService(VendorSubscriptionRepository subscriptions) {
        this.subscriptions = subscriptions;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Entitlement> of(UUID vendorId) {
        return subscriptions.findByVendorPublicId(vendorId).map(VendorEntitlementsService::toEntitlement);
    }

    @Override
    @Transactional(readOnly = true)
    public void requireTrading(UUID vendorId) {
        VendorSubscription subscription = subscriptions.findByVendorPublicId(vendorId)
                .orElseThrow(() -> new PaymentRequiredException(
                        "This store does not have a plan yet. Choose one to start selling.",
                        "SUBSCRIPTION_REQUIRED"));

        if (!subscription.permitsTrading()) {
            throw new PaymentRequiredException(
                    "Your %s plan expired on %s. Renew it to start selling again."
                            .formatted(subscription.getPlan().getName(),
                                    DATE.format(subscription.accessEndsAt())),
                    "SUBSCRIPTION_EXPIRED");
        }
    }

    private static Entitlement toEntitlement(VendorSubscription subscription) {
        Plan plan = subscription.getPlan();

        return new Entitlement(
                subscription.permitsTrading(),
                subscription.getStatus(),
                plan.getCode(),
                subscription.getCurrentPeriodEnd(),
                subscription.accessEndsAt(),
                plan.getMaxProducts(),
                plan.getMaxStaff(),
                plan.getCommissionBasisPoints());
    }
}
