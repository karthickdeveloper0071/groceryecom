package com.groceryecom.modules.billing.application;

/**
 * Which gateway takes the next payment.
 *
 * <p>A port, like {@link PaymentGateway} itself, so the services that charge money depend
 * on the decision rather than on the adapters that implement it - {@code application}
 * never imports {@code infrastructure}.
 *
 * <p>The answer can change between one payment and the next, because it comes from
 * credentials an admin edits in the console.
 */
public interface PaymentGatewaySelector {

    PaymentGateway current();
}
