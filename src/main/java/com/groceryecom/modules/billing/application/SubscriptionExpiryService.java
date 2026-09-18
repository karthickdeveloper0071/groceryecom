package com.groceryecom.modules.billing.application;

import com.groceryecom.modules.billing.contract.SubscriptionEvents;
import com.groceryecom.modules.billing.contract.SubscriptionStatus;
import com.groceryecom.modules.billing.domain.VendorSubscription;
import com.groceryecom.modules.billing.domain.VendorSubscriptionRepository;
import com.groceryecom.modules.vendor.contract.VendorPlanState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

/**
 * Time passing is the only thing that ends a licence, and nothing in a web request
 * notices time passing. This is what does.
 *
 * <p>Two steps, in one pass:
 * <ol>
 *   <li>a period that ended without payment moves to {@code PAST_DUE}. The store keeps
 *       selling through its grace days and is told, in plain words, that payment
 *       failed;</li>
 *   <li>grace running out moves it to {@code EXPIRED} and takes the store off the
 *       storefront.</li>
 * </ol>
 *
 * <p>Safe to run on every instance at once, which matters because there are three to
 * eight of them and no scheduler lock: each transition is guarded by the status it
 * starts from, so a second run finds nothing left to do rather than expiring anything
 * twice. It is also the correction for a lost call to the vendor module - the store's
 * licence flag is re-asserted from billing's own state on every pass.
 */
@Slf4j
@Service
public class SubscriptionExpiryService {

    private static final EnumSet<SubscriptionStatus> STILL_TRADING =
            EnumSet.of(SubscriptionStatus.TRIALING, SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE);

    private final VendorSubscriptionRepository subscriptions;
    private final VendorPlanState vendorPlanState;
    private final ApplicationEventPublisher events;

    SubscriptionExpiryService(VendorSubscriptionRepository subscriptions, VendorPlanState vendorPlanState,
                              ApplicationEventPublisher events) {
        this.subscriptions = subscriptions;
        this.vendorPlanState = vendorPlanState;
        this.events = events;
    }

    /** @return how many licences changed state, for the log and for a metric later */
    @Transactional
    public int settleDueSubscriptions(Instant now) {
        List<VendorSubscription> due =
                subscriptions.findByStatusInAndCurrentPeriodEndBefore(STILL_TRADING, now);

        int changed = 0;
        for (VendorSubscription subscription : due) {
            changed += subscription.isOutOfGraceAt(now) ? expire(subscription) : markPastDue(subscription);
        }

        if (changed > 0) {
            log.info("Licences settled: {} of {} due subscriptions changed state", changed, due.size());
        }
        return changed;
    }

    private int markPastDue(VendorSubscription subscription) {
        if (subscription.getStatus() == SubscriptionStatus.PAST_DUE) {
            return 0;
        }
        subscription.periodEnded();
        subscriptions.save(subscription);

        if (subscription.permitsTrading()) {
            events.publishEvent(new SubscriptionEvents.SubscriptionPastDue(
                    subscription.getVendorPublicId(), subscription.getPlan().getCode(),
                    subscription.accessEndsAt()));
        } else {
            // A cancelled licence simply ends when its period does; nothing is chased
            closeStore(subscription);
        }
        return 1;
    }

    private int expire(VendorSubscription subscription) {
        subscription.expire();
        subscriptions.save(subscription);
        closeStore(subscription);

        events.publishEvent(new SubscriptionEvents.SubscriptionExpired(
                subscription.getVendorPublicId(), subscription.getPlan().getCode(),
                subscription.accessEndsAt()));

        log.info("Vendor {} stopped selling: {} plan ended at {}", subscription.getVendorPublicId(),
                subscription.getPlan().getCode(), subscription.accessEndsAt());
        return 1;
    }

    private void closeStore(VendorSubscription subscription) {
        vendorPlanState.planExpired(subscription.getVendorPublicId(), subscription.accessEndsAt());
    }
}
