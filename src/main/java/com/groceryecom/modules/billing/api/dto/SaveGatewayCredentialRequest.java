package com.groceryecom.modules.billing.api.dto;

import com.groceryecom.modules.billing.domain.PaymentGatewayCredential.GatewayMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The keys an admin pastes from the Razorpay dashboard.
 *
 * <p>This is the one request in the platform that carries a live secret, so it is also
 * the one that must never be logged. {@code RequestLoggingFilter} masks fields whose name
 * looks like a secret, which is why these are called {@code keySecret} and
 * {@code webhookSecret} rather than anything more imaginative.
 *
 * @param mode          TEST or LIVE. Test keys move no money; being explicit about it
 *                      makes "why has nothing settled?" answerable from a screen
 * @param keyId         Razorpay Key Id, e.g. rzp_test_XXXXXXXX. Public
 * @param keySecret     Razorpay Key Secret. Stored encrypted, never returned
 * @param webhookSecret the secret set on the Razorpay webhook. Leave out when rotating
 *                      the API key to keep the one already stored; without it, webhooks
 *                      cannot be verified and are refused
 * @param enabled       false installs the keys without switching the gateway on
 */
public record SaveGatewayCredentialRequest(
        @NotNull GatewayMode mode,

        @NotBlank @Size(max = 100)
        String keyId,

        @NotBlank @Size(max = 200)
        String keySecret,

        @Size(max = 200)
        String webhookSecret,

        Boolean enabled) {
}
