package com.groceryecom.modules.billing.api.dto;

import com.groceryecom.modules.billing.contract.SubscriptionStatus;

import java.time.Instant;

/**
 * A store's licence, shaped so a dashboard can render it without doing arithmetic or
 * writing its own copy.
 *
 * <p>{@code message} is the sentence to show the vendor: "Your plan expired on 14 March.
 * Renew to start selling again." Every client shows the same words, in one place to
 * change, and nobody has to decide what "PAST_DUE" means to a shopkeeper.
 *
 * @param trading      may the store sell right now
 * @param periodEnd    when the paid period ends
 * @param accessEndsAt when selling actually stops: the period end plus grace days
 * @param daysLeft     days until {@code accessEndsAt}; 0 once it has passed
 * @param message      what to show the vendor about the state of their plan
 */
public record SubscriptionResponse(
        SubscriptionStatus status,
        boolean trading,
        String planCode,
        String planName,
        Instant periodStart,
        Instant periodEnd,
        Instant accessEndsAt,
        long daysLeft,
        Instant cancelledAt,
        String message) {
}
