package com.groceryecom.modules.billing.mapper;

import com.groceryecom.modules.billing.api.dto.SubscriptionResponse;
import com.groceryecom.modules.billing.domain.BillingPeriod;
import com.groceryecom.modules.billing.domain.Plan;
import com.groceryecom.modules.billing.domain.VendorSubscription;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the vendor is actually told.
 *
 * <p>The status code is for the client; the sentence is for the shopkeeper. "PAST_DUE"
 * means nothing to somebody who runs a grocery, so these assertions are about the words:
 * what happened, what it costs them, and by when.
 */
class BillingMapperTest {

    private static final Duration GRACE = Duration.ofDays(3);
    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");

    @Test
    void aTrialSaysWhenItEndsAndWhatToDoAboutIt() {
        VendorSubscription subscription = VendorSubscription.start(UUID.randomUUID(), plan(14), NOW, GRACE);

        SubscriptionResponse response = BillingMapper.toResponse(subscription, NOW);

        assertThat(response.trading()).isTrue();
        assertThat(response.message())
                .contains("free trial")
                .contains("15 March 2026")
                .contains("Pay before then");
        assertThat(response.daysLeft()).isEqualTo(17);
    }

    @Test
    void aMissedPaymentSaysTheStoreIsStillOpenAndUntilWhen() {
        VendorSubscription subscription = active();
        subscription.periodEnded();

        SubscriptionResponse response = BillingMapper.toResponse(subscription, NOW.plus(32, ChronoUnit.DAYS));

        assertThat(response.trading()).isTrue();
        assertThat(response.message())
                .contains("not received payment")
                .contains("stays open until 4 April 2026")
                .contains("Pay now");
    }

    /** The one the vendor will read in a panic. It must say the date and the way back. */
    @Test
    void anExpiredPlanSaysWhenItEndedAndHowToComeBack() {
        VendorSubscription subscription = active();
        subscription.periodEnded();
        subscription.expire();

        SubscriptionResponse response = BillingMapper.toResponse(subscription, NOW.plus(40, ChronoUnit.DAYS));

        assertThat(response.trading()).isFalse();
        assertThat(response.message())
                .contains("expired on 4 April 2026")
                .contains("no longer selling")
                .contains("Renew");
        assertThat(response.daysLeft()).isZero();
    }

    @Test
    void anActivePlanSaysWhenItRenews() {
        SubscriptionResponse response = BillingMapper.toResponse(active(), NOW);

        assertThat(response.message()).contains("renews on 1 April 2026");
    }

    @Test
    void aCancelledPlanThatIsStillPaidForSaysSo() {
        VendorSubscription subscription = active();
        subscription.cancel(NOW);

        SubscriptionResponse response = BillingMapper.toResponse(subscription, NOW);

        assertThat(response.trading()).isTrue();
        assertThat(response.message())
                .contains("cancelled")
                .contains("keeps selling until 1 April 2026");
    }

    private static VendorSubscription active() {
        VendorSubscription subscription = VendorSubscription.start(UUID.randomUUID(), plan(0), NOW, GRACE);
        subscription.paymentReceived(NOW, GRACE);
        return subscription;
    }

    private static Plan plan(int trialDays) {
        Plan plan = new Plan();
        plan.setId(1L);
        plan.setCode("STARTER");
        plan.setName("Starter");
        plan.setPriceAmountMinor(4900L);
        plan.setPriceCurrency("MYR");
        plan.setBillingPeriod(BillingPeriod.MONTHLY);
        plan.setTrialDays(trialDays);
        plan.setCommissionBasisPoints(500);
        return plan;
    }
}
