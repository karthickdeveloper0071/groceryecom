package com.groceryecom.modules.billing.application;

import com.groceryecom.shared.money.Money;

import java.util.UUID;

/**
 * How the platform asks for money. A port, so choosing a provider is one class and one
 * property rather than a change to every service that charges.
 *
 * <p>The only implementation today is {@code ManualPaymentGateway}: the store transfers
 * the money and an admin confirms it. That is how a marketplace actually starts, and it
 * exercises the whole flow - a charge is created, a reference identifies it, something
 * outside the system settles it - so adding a real provider later is an adapter, not a
 * redesign.
 *
 * <p>Whatever the provider, settlement arrives separately (an admin confirming, a
 * webhook) and may arrive twice. {@code ConfirmSubscriptionPaymentService} is what makes
 * that harmless.
 */
public interface PaymentGateway {

    /** Names the provider in stored payments, e.g. MANUAL. */
    String provider();

    /**
     * Asks for money and returns what the client needs to pay.
     *
     * @param vendorId  the store being charged, for the provider's own records
     * @param amount    the plan's price
     * @param reference an id the platform generates, unique per charge
     */
    PaymentInstruction requestPayment(UUID vendorId, Money amount, String reference);

    /**
     * What to show the vendor so the money can be sent.
     *
     * @param reference    the charge's id, used to confirm it later
     * @param instructions human-readable next step
     * @param redirectUrl  where to send the browser, when the provider has a page; null otherwise
     */
    record PaymentInstruction(String reference, String instructions, String redirectUrl) {
    }
}
