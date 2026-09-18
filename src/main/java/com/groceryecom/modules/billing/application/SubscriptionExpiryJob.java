package com.groceryecom.modules.billing.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Runs {@link SubscriptionExpiryService} on a clock.
 *
 * <p>Hourly, not nightly: a store that pays at nine in the morning should not wait until
 * midnight to sell, and one whose licence ran out at nine should not keep selling all
 * day. The two are the same setting, and an hour is the compromise.
 *
 * <p>Deliberately thin. Scheduling is infrastructure and the decision is business, so
 * the rules live in the service, where a test can call them with any {@code now} it
 * likes instead of waiting for the hour to turn.
 *
 * <p>Every instance runs this; the service is written so that is harmless. A scheduler
 * lock (ShedLock, or a leader election) becomes worth adding when a job here sends
 * email or moves money, because a duplicate of those is not harmless.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.billing.expiry-job.enabled", havingValue = "true", matchIfMissing = true)
class SubscriptionExpiryJob {

    private final SubscriptionExpiryService expiryService;

    SubscriptionExpiryJob(SubscriptionExpiryService expiryService) {
        this.expiryService = expiryService;
    }

    @Scheduled(cron = "${app.billing.expiry-job.cron:0 5 * * * *}")
    void settleDueSubscriptions() {
        try {
            expiryService.settleDueSubscriptions(Instant.now());
        } catch (RuntimeException e) {
            // A scheduled method that throws is silently not retried, and the next run is
            // an hour away, so the failure is recorded rather than lost
            log.error("Settling due subscriptions failed; the next run will pick them up", e);
        }
    }
}
