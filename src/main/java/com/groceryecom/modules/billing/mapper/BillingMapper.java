package com.groceryecom.modules.billing.mapper;

import com.groceryecom.modules.billing.api.dto.PlanResponse;
import com.groceryecom.modules.billing.api.dto.SubscriptionResponse;
import com.groceryecom.modules.billing.domain.Plan;
import com.groceryecom.modules.billing.domain.VendorSubscription;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Keeps entities out of API responses, and keeps what the vendor is told about their
 * plan in one place.
 *
 * <p>The message matters as much as the status. "PAST_DUE" means nothing to a
 * shopkeeper; "We could not take payment. Your store stays open until 14 March" tells
 * them what happened, what it costs them and by when. Every client shows the same
 * sentence, so the words change here and nowhere else.
 */
public final class BillingMapper {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d MMMM yyyy").withZone(ZoneOffset.UTC);

    private BillingMapper() {
    }

    public static PlanResponse toResponse(Plan plan) {
        return new PlanResponse(
                plan.getCode(),
                plan.getName(),
                plan.getDescription(),
                plan.getPriceAmountMinor(),
                plan.price().toDecimal(),
                plan.getPriceCurrency(),
                plan.getBillingPeriod().name(),
                plan.getTrialDays(),
                plan.getMaxProducts(),
                plan.getMaxStaff(),
                plan.getCommissionBasisPoints());
    }

    public static SubscriptionResponse toResponse(VendorSubscription subscription, Instant now) {
        return new SubscriptionResponse(
                subscription.getStatus(),
                subscription.permitsTrading(),
                subscription.getPlan().getCode(),
                subscription.getPlan().getName(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                subscription.accessEndsAt(),
                daysLeft(subscription.accessEndsAt(), now),
                subscription.getCancelledAt(),
                message(subscription, now));
    }

    private static long daysLeft(Instant until, Instant now) {
        long days = Duration.between(now, until).toDays();
        return Math.max(days, 0);
    }

    private static String message(VendorSubscription subscription, Instant now) {
        String planName = subscription.getPlan().getName();

        // A cancelled plan keeps running until the paid period ends, so its status is
        // still ACTIVE. Telling that vendor their plan "renews" would be a lie they only
        // discover when the store goes quiet.
        if (subscription.getCancelledAt() != null) {
            return cancelledMessage(subscription, planName, now);
        }

        return switch (subscription.getStatus()) {
            case PENDING_PAYMENT -> "Your %s plan starts as soon as your payment is confirmed."
                    .formatted(planName);
            case TRIALING -> "You are on a free trial of the %s plan until %s. Pay before then to keep selling."
                    .formatted(planName, DATE.format(subscription.getCurrentPeriodEnd()));
            case ACTIVE -> "Your %s plan renews on %s."
                    .formatted(planName, DATE.format(subscription.getCurrentPeriodEnd()));
            // The one that has to be unmistakable: the store is still open, but not for long
            case PAST_DUE -> ("We have not received payment for your %s plan. Your store stays open "
                    + "until %s, then it will stop selling. Pay now to avoid that.")
                    .formatted(planName, DATE.format(subscription.accessEndsAt()));
            case EXPIRED -> ("Your %s plan expired on %s and your store is no longer selling. "
                    + "Renew to put it back on the storefront.")
                    .formatted(planName, DATE.format(subscription.accessEndsAt()));
            case CANCELLED -> cancelledMessage(subscription, planName, now);
        };
    }

    private static String cancelledMessage(VendorSubscription subscription, String planName, Instant now) {
        if (subscription.permitsTrading()) {
            return "Your %s plan is cancelled. Your store keeps selling until %s, the end of the period you paid for."
                    .formatted(planName, DATE.format(subscription.getCurrentPeriodEnd()));
        }
        return "Your %s plan is cancelled and your store is not selling. Choose a plan to start again."
                .formatted(planName);
    }
}
