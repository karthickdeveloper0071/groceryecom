package com.groceryecom.modules.billing.api.dto;

import com.groceryecom.modules.billing.domain.PaymentGatewayCredential;
import com.groceryecom.modules.billing.domain.PaymentGatewayCredential.GatewayMode;

import java.time.Instant;
import java.util.UUID;

/**
 * What the admin console is allowed to see about an installed gateway.
 *
 * <p>There is no secret field, and there never will be. A secret that can be read back
 * through an API is a secret that leaks through a screenshot, a browser cache or an
 * over-broad admin account. {@code keyIdHint} is the last four characters of the public
 * key id, which is enough for a person to tell "test keys from January" from "live keys
 * from launch day".
 *
 * @param webhooksVerified whether a webhook secret is stored. False means the platform
 *                         cannot tell a real Razorpay callback from a forged one, and
 *                         refuses all of them
 * @param updatedBy        the admin who last changed these keys
 */
public record GatewayCredentialResponse(
        UUID id,
        String provider,
        GatewayMode mode,
        String keyIdHint,
        boolean enabled,
        boolean webhooksVerified,
        UUID updatedBy,
        Instant updatedAt) {

    public static GatewayCredentialResponse of(PaymentGatewayCredential credential) {
        return new GatewayCredentialResponse(
                credential.getPublicId(),
                credential.getProvider(),
                credential.getMode(),
                credential.keyIdHint(),
                credential.isUsable(),
                credential.getWebhookSecretCipher() != null,
                credential.getUpdatedByUserId(),
                credential.getUpdatedAt() != null ? credential.getUpdatedAt() : credential.getCreatedAt());
    }
}
