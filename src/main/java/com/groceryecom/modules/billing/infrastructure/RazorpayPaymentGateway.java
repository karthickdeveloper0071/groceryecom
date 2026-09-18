package com.groceryecom.modules.billing.infrastructure;

import com.groceryecom.modules.billing.application.GatewayCredentials;
import com.groceryecom.modules.billing.application.GatewayCredentialsService;
import com.groceryecom.modules.billing.application.PaymentGateway;
import com.groceryecom.shared.exception.ApplicationException;
import com.groceryecom.shared.money.Money;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Razorpay, called over its REST API.
 *
 * <p>No SDK: creating an order is one POST, and a dependency that wraps one POST brings
 * its own transitive tree, its own release cycle and its own opinions about HTTP clients.
 *
 * <p>How a payment happens:
 * <ol>
 *   <li>here, the platform creates a Razorpay <b>order</b> and returns its id plus the
 *       public key id;</li>
 *   <li>the frontend opens Razorpay Checkout with those two values, and the vendor pays
 *       Razorpay directly - no card detail ever reaches this application, which is what
 *       keeps it out of PCI scope;</li>
 *   <li>Razorpay calls {@code RazorpayWebhookController}, which settles the payment. The
 *       browser is never trusted to report success: a page can be closed, refreshed, or
 *       faked.</li>
 * </ol>
 *
 * <p>Keys come from the database, where an admin pasted them, and are read per call
 * rather than cached in a field: rotating a key in the console takes effect on the next
 * payment, not on the next restart.
 *
 * <p><b>Currency:</b> Razorpay settles INR for a standard Indian account; other
 * currencies need International Payments enabled on the account. The amount sent here is
 * whatever the plan says, so a plan priced in MYR against an INR-only account is rejected
 * by Razorpay at order creation - loudly, at configuration time, which is the right place
 * to find out.
 */
@Slf4j
@Component
class RazorpayPaymentGateway implements PaymentGateway {

    static final String PROVIDER = "RAZORPAY";
    private static final String ORDERS_URL = "/v1/orders";

    private final GatewayCredentialsService credentials;
    private final RestClient restClient;

    @Autowired
    RazorpayPaymentGateway(GatewayCredentialsService credentials, RazorpayProperties properties) {
        this(credentials, RestClient.builder(), properties);
    }

    /** Takes a builder so a test can bind a stub to it instead of calling Razorpay. */
    RazorpayPaymentGateway(GatewayCredentialsService credentials, RestClient.Builder restClientBuilder,
                           RazorpayProperties properties) {
        this.credentials = credentials;
        this.restClient = restClientBuilder.baseUrl(properties.apiUrl()).build();
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    /** True when an admin has installed keys and left the gateway switched on. */
    boolean isConfigured() {
        return credentials.usable(PROVIDER).isPresent();
    }

    @Override
    public PaymentInstruction requestPayment(UUID vendorId, Money amount, String reference) {
        GatewayCredentials keys = credentials.usable(PROVIDER)
                .orElseThrow(() -> new GatewayUnavailableException(
                        "Card payment is not available at the moment. Try again shortly."));

        Map<String, Object> order = createOrder(keys, amount, reference, vendorId);
        String orderId = String.valueOf(order.get("id"));

        log.info("Razorpay order {} created for vendor {}, {} ({} mode)", orderId, vendorId, amount,
                keys.mode());

        // The order id is the reference the webhook will quote, so it is what the payment
        // row is keyed by; the key id goes to the browser to open Checkout.
        return new PaymentInstruction(orderId,
                "Pay %s with card, UPI or netbanking to start your plan.".formatted(amount),
                null, keys.keyId());
    }

    private Map<String, Object> createOrder(GatewayCredentials keys, Money amount, String reference,
                                            UUID vendorId) {
        Map<String, Object> request = Map.of(
                // Razorpay takes the amount in the currency's smallest unit, which is how
                // Money already holds it: no conversion, no rounding, nothing to get wrong
                "amount", amount.amountMinor(),
                "currency", amount.currency().getCurrencyCode(),
                // Our own id for this charge. Razorpay echoes it back in the webhook and
                // rejects a repeat, so a retried request cannot create two orders.
                "receipt", reference,
                // Razorpay captures automatically; leaving it manual means money is
                // authorised and never taken, which looks like a paid vendor who is not
                "payment_capture", 1,
                "notes", Map.of("vendorId", vendorId.toString(), "reference", reference));

        try {
            return restClient.post()
                    .uri(ORDERS_URL)
                    .header(HttpHeaders.AUTHORIZATION, basicAuth(keys))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(Map.class);
        } catch (RuntimeException e) {
            // Razorpay's own message can name the account or the key; it is logged, never
            // returned. The vendor gets something they can act on.
            log.error("Razorpay order creation failed for vendor {}: {}", vendorId, e.getMessage());
            throw new GatewayUnavailableException(
                    "We could not start the payment. Try again in a moment.");
        }
    }

    private static String basicAuth(GatewayCredentials keys) {
        String value = keys.keyId() + ":" + keys.keySecret();
        return "Basic " + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    /** 503: the gateway, not the request, is the problem. Retrying later is the right advice. */
    static class GatewayUnavailableException extends ApplicationException {

        GatewayUnavailableException(String message) {
            super(message, "PAYMENT_GATEWAY_UNAVAILABLE", 503);
        }
    }
}
