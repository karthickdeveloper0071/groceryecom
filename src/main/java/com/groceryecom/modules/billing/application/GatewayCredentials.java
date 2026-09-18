package com.groceryecom.modules.billing.application;

import com.groceryecom.modules.billing.domain.PaymentGatewayCredential.GatewayMode;

/**
 * A gateway's keys in usable form, held only for the length of one call.
 *
 * <p>Not an entity, not a DTO, and never serialised: this type exists so the decrypted
 * secret has an owner and a short life. It must not be logged, put in a response, cached,
 * or stored in a field of a singleton.
 *
 * @param keyId         public; the browser needs it
 * @param keySecret     private; used only to sign calls to the gateway
 * @param webhookSecret private; used only to verify that a webhook came from the gateway.
 *                      Null until an admin enters one, which means webhooks cannot be
 *                      trusted yet and are refused.
 */
public record GatewayCredentials(String keyId, String keySecret, String webhookSecret, GatewayMode mode) {

    public boolean canVerifyWebhooks() {
        return webhookSecret != null && !webhookSecret.isBlank();
    }

    /** Never print the secret, whatever a debugger, a log line or an error page asks for. */
    @Override
    public String toString() {
        return "GatewayCredentials[keyId=%s, mode=%s, secret=hidden]".formatted(keyId, mode);
    }
}
