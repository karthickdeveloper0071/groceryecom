/**
 * Vendor licensing: the plans a store can buy, the licence it holds, and the payments
 * that keep it alive.
 *
 * <p>Not to be confused with the planned {@code payment} module, which is about
 * customers' money: order payments, refunds and payouts to vendors. This module is
 * about the platform's own subscription revenue, which has a different lifecycle
 * (periods, renewals, expiry) and different failure modes.
 *
 * <p>Other modules may only use the {@code contract} package.
 */
@ApplicationModule(displayName = "Billing & vendor licensing")
package com.groceryecom.modules.billing;

import org.springframework.modulith.ApplicationModule;
