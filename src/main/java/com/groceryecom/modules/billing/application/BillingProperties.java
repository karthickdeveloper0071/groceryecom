package com.groceryecom.modules.billing.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Licensing policy, under {@code app.billing}.
 *
 * <p>These are commercial decisions, not technical ones, so they are configuration: the
 * people who decide how long a late payer keeps selling should not need a release to
 * change their minds.
 *
 * @param provider    which payment gateway is wired in; {@code manual} is bank transfer
 *                    confirmed by an admin
 * @param gracePeriod how long a store keeps trading after its period ends without
 *                    payment. Zero would switch a shop off the morning a card expires,
 *                    which loses the vendor rather than the debt.
 * @param expiryJob   how often licences are checked; see {@code SubscriptionExpiryJob}
 */
@Validated
@ConfigurationProperties("app.billing")
public record BillingProperties(String provider, Duration gracePeriod, ExpiryJob expiryJob) {

    public BillingProperties {
        provider = provider != null ? provider : "manual";
        gracePeriod = gracePeriod != null ? gracePeriod : Duration.ofDays(3);
        expiryJob = expiryJob != null ? expiryJob : new ExpiryJob(true, "0 5 * * * *");
    }

    /**
     * @param enabled turned off in tests, where the service is called directly
     * @param cron    hourly by default: a store must not stay switched off for a day
     *                after paying, and must not keep selling for a day after expiring
     */
    public record ExpiryJob(boolean enabled, String cron) {
    }
}
