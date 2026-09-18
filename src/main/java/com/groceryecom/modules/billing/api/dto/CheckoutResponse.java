package com.groceryecom.modules.billing.api.dto;

import java.math.BigDecimal;

/**
 * What the vendor gets back when they choose a plan: the licence as it stands now, and
 * how to pay for it.
 *
 * <p>{@code payment} is null when nothing is owed yet - a plan with a free trial starts
 * immediately, and the store is told to come back before the trial ends rather than
 * being asked for money it does not owe.
 */
public record CheckoutResponse(SubscriptionResponse subscription, Payment payment) {

    /**
     * @param reference    quote this when paying; it is also what confirms the payment.
     *                     With Razorpay it is the order id, which the frontend passes to
     *                     Checkout as {@code order_id} and the webhook quotes back
     * @param instructions the next step, in words the vendor can act on
     * @param redirectUrl  a provider's payment page, when there is one
     * @param publicKey    the gateway's public key for the browser widget: Razorpay's
     *                     {@code key}. Null when the provider needs none. Never a secret -
     *                     it identifies the merchant, it authorises nothing
     */
    public record Payment(String reference, long amountMinor, BigDecimal amount, String currency,
                          String provider, String instructions, String redirectUrl, String publicKey) {
    }
}
