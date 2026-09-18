package com.groceryecom.modules.billing.infrastructure;

import com.groceryecom.modules.billing.application.ConfirmSubscriptionPaymentService;
import com.groceryecom.modules.billing.application.GatewayCredentials;
import com.groceryecom.modules.billing.application.GatewayCredentialsService;
import com.groceryecom.shared.exception.UnauthorizedException;
import com.groceryecom.shared.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

/**
 * Where Razorpay tells the platform that money arrived.
 *
 * <p>This endpoint is public, because Razorpay cannot hold a bearer token. What
 * authenticates a request is the HMAC signature over the raw body, so the body is taken
 * as a {@code String} and parsed only after the signature has been checked - re-serialised
 * JSON would never match.
 *
 * <p>The browser is deliberately not trusted to report a successful payment. A page can be
 * closed before the callback, refreshed twice, or crafted by hand; this is the only path
 * that grants a licence.
 *
 * <p>Razorpay retries a webhook it believes failed, so the same event arrives more than
 * once as a matter of course. {@code ConfirmSubscriptionPaymentService} settles by the
 * payment's own status, so a repeat changes nothing. The response is 200 for anything
 * understood - including a duplicate, and including an event this platform does not care
 * about - because a non-2xx tells Razorpay to keep retrying something that will never
 * succeed.
 */
@Slf4j
@RestController
@RequestMapping("/v1/billing/webhooks")
@Tag(name = "Billing", description = "Vendor plans, licences and subscription payments")
class RazorpayWebhookController {

    private static final String SIGNATURE_HEADER = "X-Razorpay-Signature";
    private static final String PAID = "order.paid";
    private static final String CAPTURED = "payment.captured";

    private final GatewayCredentialsService credentials;
    private final ConfirmSubscriptionPaymentService confirmPayment;
    private final JsonMapper jsonMapper;

    RazorpayWebhookController(GatewayCredentialsService credentials,
                              ConfirmSubscriptionPaymentService confirmPayment, JsonMapper jsonMapper) {
        this.credentials = credentials;
        this.confirmPayment = confirmPayment;
        this.jsonMapper = jsonMapper;
    }

    @PostMapping(value = "/razorpay", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Razorpay webhook",
            description = "Called by Razorpay, not by clients. Authenticated by the "
                    + "X-Razorpay-Signature header over the raw body; unsigned or wrongly "
                    + "signed calls are refused. Safe to deliver more than once.")
    ApiResponse<Void> razorpay(@RequestBody String rawBody,
                               @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature) {
        GatewayCredentials keys = credentials.usable(RazorpayPaymentGateway.PROVIDER)
                .filter(GatewayCredentials::canVerifyWebhooks)
                .orElseThrow(() -> {
                    // No webhook secret means nothing can be verified, so nothing is trusted
                    log.warn("Razorpay webhook received but no webhook secret is configured");
                    return new UnauthorizedException("Webhook verification is not configured");
                });

        if (!RazorpaySignature.isValid(rawBody, signature, keys.webhookSecret())) {
            // Worth an alert: either a misconfigured secret after a rotation, or somebody
            // trying to grant themselves a licence
            log.warn("Razorpay webhook rejected: signature did not match");
            throw new UnauthorizedException("Invalid webhook signature");
        }

        handle(rawBody);
        return ApiResponse.ok(null, "Received");
    }

    private void handle(String rawBody) {
        JsonNode event = jsonMapper.readTree(rawBody);
        String type = event.path("event").asString("");

        if (!PAID.equals(type) && !CAPTURED.equals(type)) {
            // Razorpay sends many event types; the ones not subscribed to are acknowledged
            // and ignored rather than retried forever
            log.debug("Razorpay event {} ignored", type);
            return;
        }

        Optional<String> orderId = orderId(event);
        if (orderId.isEmpty()) {
            log.warn("Razorpay event {} carried no order id; ignoring", type);
            return;
        }

        // Null actor: no admin did this, Razorpay did. The audit line says so.
        confirmPayment.execute(null, orderId.get());
        log.info("Razorpay event {} settled order {}", type, orderId.get());
    }

    /**
     * The order id is the reference the payment was stored under. It sits in a different
     * place depending on the event, which is why this is one method rather than a field
     * name repeated twice.
     */
    private static Optional<String> orderId(JsonNode event) {
        JsonNode entities = event.path("payload");

        String fromOrder = entities.path("order").path("entity").path("id").asString("");
        if (!fromOrder.isEmpty()) {
            return Optional.of(fromOrder);
        }
        String fromPayment = entities.path("payment").path("entity").path("order_id").asString("");
        return fromPayment.isEmpty() ? Optional.empty() : Optional.of(fromPayment);
    }
}
