package com.groceryecom.modules.billing.domain;

import java.time.Instant;
import java.time.ZoneOffset;

/**
 * How long one paid period lasts.
 *
 * <p>Months are added as calendar months, not as 30 days: a store that pays on the 31st
 * of January is paid until the 28th of February, which is what a person expects and
 * what an invoice has to say. The arithmetic is done in UTC, the same clock everything
 * else in the platform uses.
 */
public enum BillingPeriod {

    MONTHLY(1),
    YEARLY(12);

    private final int months;

    BillingPeriod(int months) {
        this.months = months;
    }

    public Instant endFrom(Instant start) {
        return start.atZone(ZoneOffset.UTC).plusMonths(months).toInstant();
    }
}
