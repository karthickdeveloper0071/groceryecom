package com.groceryecom.modules.billing.infrastructure;

import com.groceryecom.modules.billing.application.PaymentGateway;
import com.groceryecom.shared.money.Money;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Bank transfer, confirmed by an admin. The provider a marketplace starts with, before
 * a gateway account exists.
 *
 * <p>It creates a reference the vendor quotes with their transfer, and an admin
 * confirms that reference once the money lands. Nothing here talks to an external
 * system, so it cannot fail in the ways a gateway can - which is also why the code
 * that settles a payment is written as if it could.
 *
 * <p>A real provider (Stripe, Razorpay, Billplz) is another class implementing
 * {@link PaymentGateway}, plus a webhook endpoint that calls the same confirmation
 * service this admin action calls. Nothing else changes.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.billing.provider", havingValue = "manual", matchIfMissing = true)
class ManualPaymentGateway implements PaymentGateway {

    private static final String PROVIDER = "MANUAL";

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public PaymentInstruction requestPayment(UUID vendorId, Money amount, String reference) {
        log.info("Manual payment {} requested for vendor {}: {}", reference, vendorId, amount);

        return new PaymentInstruction(reference,
                ("Transfer %s quoting reference %s. Your plan starts as soon as the payment is "
                        + "confirmed, usually within one business day.").formatted(amount, reference),
                null);
    }
}
