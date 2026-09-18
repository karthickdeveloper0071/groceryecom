package com.groceryecom.modules.billing.domain;

import com.groceryecom.modules.billing.contract.SubscriptionStatus;
import com.groceryecom.shared.exception.ConflictException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * When a licence starts, ends and comes back. These rules decide whether a shop is open
 * for business, so they are the entity's own and hold for every caller: a vendor paying,
 * an admin fixing something, or the job that runs at night.
 */
class VendorSubscriptionTest {

    private static final UUID VENDOR = UUID.randomUUID();
    private static final Duration GRACE = Duration.ofDays(3);
    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");

    @Test
    void aPlanWithATrialStartsTradingImmediatelyAndOwesNothing() {
        VendorSubscription subscription = VendorSubscription.start(VENDOR, plan(14), NOW, GRACE);

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.TRIALING);
        assertThat(subscription.permitsTrading()).isTrue();
        assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(NOW.plus(14, ChronoUnit.DAYS));
    }

    @Test
    void aPlanWithoutATrialWaitsForTheMoney() {
        VendorSubscription subscription = VendorSubscription.start(VENDOR, plan(0), NOW, GRACE);

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PENDING_PAYMENT);
        assertThat(subscription.permitsTrading()).isFalse();
    }

    @Test
    void paymentStartsAMonthAndOpensTheStore() {
        VendorSubscription subscription = VendorSubscription.start(VENDOR, plan(0), NOW, GRACE);

        subscription.paymentReceived(NOW, GRACE);

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(Instant.parse("2026-04-01T10:00:00Z"));
        // Grace is on top of the paid period, never inside it
        assertThat(subscription.accessEndsAt()).isEqualTo(Instant.parse("2026-04-04T10:00:00Z"));
    }

    /** Paying early must never cost a store the days it already paid for. */
    @Test
    void renewingBeforeTheEndExtendsFromTheEndDate() {
        VendorSubscription subscription = VendorSubscription.start(VENDOR, plan(0), NOW, GRACE);
        subscription.paymentReceived(NOW, GRACE);

        subscription.paymentReceived(NOW.plus(20, ChronoUnit.DAYS), GRACE);

        // From 1 April, not from 21 March
        assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(Instant.parse("2026-05-01T10:00:00Z"));
    }

    /** Nobody should pay for the month their store spent switched off. */
    @Test
    void payingAfterExpiryStartsAFreshMonthFromToday() {
        VendorSubscription subscription = VendorSubscription.start(VENDOR, plan(0), NOW, GRACE);
        subscription.paymentReceived(NOW, GRACE);
        subscription.periodEnded();
        subscription.expire();

        Instant late = Instant.parse("2026-06-10T10:00:00Z");
        subscription.paymentReceived(late, GRACE);

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(Instant.parse("2026-07-10T10:00:00Z"));
    }

    @Test
    void aMissedPaymentKeepsTheStoreSellingThroughItsGraceDays() {
        VendorSubscription subscription = VendorSubscription.start(VENDOR, plan(0), NOW, GRACE);
        subscription.paymentReceived(NOW, GRACE);

        subscription.periodEnded();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(subscription.permitsTrading()).isTrue();
    }

    @Test
    void graceRunningOutStopsTheStoreSelling() {
        VendorSubscription subscription = VendorSubscription.start(VENDOR, plan(0), NOW, GRACE);
        subscription.paymentReceived(NOW, GRACE);
        subscription.periodEnded();

        subscription.expire();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(subscription.permitsTrading()).isFalse();
    }

    /** Taking the shop away the day they cancel would be keeping the month's money for nothing. */
    @Test
    void cancellingLeavesTheStoreSellingUntilTheEndOfThePaidPeriod() {
        VendorSubscription subscription = VendorSubscription.start(VENDOR, plan(0), NOW, GRACE);
        subscription.paymentReceived(NOW, GRACE);

        subscription.cancel(NOW.plus(2, ChronoUnit.DAYS));

        assertThat(subscription.permitsTrading()).isTrue();
        assertThat(subscription.getCancelledAt()).isNotNull();

        // and then simply ends, rather than being chased for payment
        subscription.periodEnded();
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
    }

    @Test
    void cancellingBeforeAnythingIsPaidClosesTheStoreAtOnce() {
        VendorSubscription subscription = VendorSubscription.start(VENDOR, plan(0), NOW, GRACE);

        subscription.cancel(NOW);

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(subscription.permitsTrading()).isFalse();
    }

    @Test
    void cancellingTwiceIsAConflictRatherThanASecondCancellation() {
        VendorSubscription subscription = VendorSubscription.start(VENDOR, plan(0), NOW, GRACE);
        subscription.cancel(NOW);

        assertThatThrownBy(() -> subscription.cancel(NOW))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already cancelled");
    }

    @Test
    void choosingThePlanTheStoreIsAlreadyOnIsRefused() {
        Plan starter = plan(14);
        VendorSubscription subscription = VendorSubscription.start(VENDOR, starter, NOW, GRACE);

        assertThatThrownBy(() -> subscription.switchTo(starter, GRACE))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already on this plan");
    }

    @Test
    void movingToAnotherPlanKeepsTheStoreSellingUntilItIsPaidFor() {
        VendorSubscription subscription = VendorSubscription.start(VENDOR, plan(14), NOW, GRACE);

        subscription.switchTo(plan(0, 2L, "GROWTH"), GRACE);

        assertThat(subscription.getPlan().getCode()).isEqualTo("GROWTH");
        assertThat(subscription.permitsTrading()).isTrue();
    }

    private static Plan plan(int trialDays) {
        return plan(trialDays, 1L, "STARTER");
    }

    private static Plan plan(int trialDays, Long id, String code) {
        Plan plan = new Plan();
        plan.setId(id);
        plan.setCode(code);
        plan.setName(code.charAt(0) + code.substring(1).toLowerCase(java.util.Locale.ROOT));
        plan.setPriceAmountMinor(4900L);
        plan.setPriceCurrency("MYR");
        plan.setBillingPeriod(BillingPeriod.MONTHLY);
        plan.setTrialDays(trialDays);
        plan.setCommissionBasisPoints(500);
        return plan;
    }
}
