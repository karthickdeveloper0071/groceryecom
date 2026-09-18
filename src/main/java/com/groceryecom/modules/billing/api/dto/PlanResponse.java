package com.groceryecom.modules.billing.api.dto;

import java.math.BigDecimal;

/**
 * A plan as the price list shows it.
 *
 * <p>The price is sent twice on purpose: {@code priceMinor} is the exact integer the
 * platform charges, and {@code price} is the same amount as a decimal for display. A
 * client that formats the minor amount itself gets the decimal place wrong for at least
 * one currency.
 *
 * @param code                  STARTER, GROWTH, SCALE - what an integration refers to
 * @param priceMinor            the price in the currency's smallest unit
 * @param price                 the same price as a decimal, e.g. 49.00
 * @param currency              ISO code, e.g. MYR
 * @param billingPeriod         MONTHLY or YEARLY
 * @param trialDays             days a store trades before paying; 0 means pay first
 * @param maxProducts           catalogue limit; null means unlimited
 * @param maxStaff              staff limit; null means unlimited
 * @param commissionBasisPoints the platform's cut per order: 250 = 2.5%
 */
public record PlanResponse(
        String code,
        String name,
        String description,
        long priceMinor,
        BigDecimal price,
        String currency,
        String billingPeriod,
        int trialDays,
        Integer maxProducts,
        Integer maxStaff,
        int commissionBasisPoints) {
}
