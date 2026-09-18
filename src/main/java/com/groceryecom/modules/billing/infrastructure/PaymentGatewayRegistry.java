package com.groceryecom.modules.billing.infrastructure;

import com.groceryecom.modules.billing.application.PaymentGateway;
import com.groceryecom.modules.billing.application.PaymentGatewaySelector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Decides which gateway takes the next payment.
 *
 * <p>Razorpay when an admin has installed keys and left it switched on; bank transfer
 * otherwise. The decision is made per payment, from the database, so three things happen
 * without a deployment: the platform starts taking cards the moment keys are pasted, a
 * key rotation takes effect on the next payment, and switching the gateway off during an
 * outage falls back to bank transfer instead of failing vendors' checkouts.
 *
 * <p>A payment already in flight is unaffected: it is settled by its own stored provider
 * and reference, not by whatever is configured when the money arrives.
 */
@Slf4j
@Component
class PaymentGatewayRegistry implements PaymentGatewaySelector {

    private final RazorpayPaymentGateway razorpay;
    private final PaymentGateway fallback;

    PaymentGatewayRegistry(RazorpayPaymentGateway razorpay, ManualPaymentGateway fallback) {
        this.razorpay = razorpay;
        this.fallback = fallback;
    }

    @Override
    public PaymentGateway current() {
        if (razorpay.isConfigured()) {
            return razorpay;
        }
        log.debug("No payment gateway configured; falling back to {}", fallback.provider());
        return fallback;
    }
}
