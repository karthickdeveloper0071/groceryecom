package com.groceryecom.modules.billing.application;

import com.groceryecom.modules.billing.api.dto.GatewayCredentialResponse;
import com.groceryecom.modules.billing.api.dto.SaveGatewayCredentialRequest;
import com.groceryecom.modules.billing.domain.PaymentGatewayCredential;
import com.groceryecom.modules.billing.domain.PaymentGatewayCredentialRepository;
import com.groceryecom.platform.audit.AuditLog;
import com.groceryecom.platform.crypto.SecretCipher;
import com.groceryecom.platform.security.AuthenticatedUser;
import com.groceryecom.shared.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Where an admin pastes the gateway keys, and the only place they are decrypted.
 *
 * <p>The platform owner signs up with Razorpay after the software is running, rotates
 * keys when Razorpay asks, and swaps test keys for live ones on launch day. Each of those
 * is a screen in the admin console here, not a deployment.
 *
 * <p>Three rules this class exists to keep:
 * <ul>
 *   <li>secrets are encrypted before they reach the database, so a dump or a backup does
 *       not contain a live key;</li>
 *   <li>no secret ever leaves through an API. {@link #list} returns the key id's last four
 *       characters and nothing else, which is enough for a person to recognise a key;</li>
 *   <li>every change is audited with the admin who made it. "Who changed the payment
 *       configuration, and when?" is the first question after money stops arriving.</li>
 * </ul>
 */
@Slf4j
@Service
public class GatewayCredentialsService {

    private final PaymentGatewayCredentialRepository credentials;
    private final SecretCipher cipher;
    private final AuditLog auditLog;

    GatewayCredentialsService(PaymentGatewayCredentialRepository credentials, SecretCipher cipher,
                              AuditLog auditLog) {
        this.credentials = credentials;
        this.cipher = cipher;
        this.auditLog = auditLog;
    }

    /**
     * Installs or replaces a provider's keys.
     *
     * <p>Replacing is the normal case - a rotation, or going live - so this is one
     * endpoint rather than a create and an update. The webhook secret is optional on an
     * update: leaving it out keeps the one already stored, so rotating the API key does
     * not silently switch webhook verification off.
     */
    @Transactional
    public GatewayCredentialResponse save(AuthenticatedUser admin, String provider,
                                          SaveGatewayCredentialRequest request) {
        String normalised = provider.toUpperCase(java.util.Locale.ROOT);

        PaymentGatewayCredential credential = credentials.findByProvider(normalised)
                .orElseGet(() -> {
                    PaymentGatewayCredential created = new PaymentGatewayCredential();
                    created.setProvider(normalised);
                    return created;
                });

        credential.setMode(request.mode());
        credential.setKeyId(request.keyId().trim());
        credential.setKeySecretCipher(cipher.encrypt(request.keySecret().trim()));
        credential.setEnabled(request.enabled() == null || request.enabled());
        credential.setUpdatedByUserId(admin.id());

        if (request.webhookSecret() != null && !request.webhookSecret().isBlank()) {
            credential.setWebhookSecretCipher(cipher.encrypt(request.webhookSecret().trim()));
        }

        PaymentGatewayCredential saved = credentials.save(credential);
        auditLog.paymentGatewayConfigured(admin.id(), normalised, saved.getMode().name(),
                saved.keyIdHint(), saved.isUsable());
        log.info("Payment gateway {} configured in {} mode, enabled={}", normalised, saved.getMode(),
                saved.isUsable());

        return GatewayCredentialResponse.of(saved);
    }

    /** What the admin console shows: which gateways exist, in which mode, never a secret. */
    @Transactional(readOnly = true)
    public List<GatewayCredentialResponse> list() {
        return credentials.findAllByOrderByProviderAsc().stream()
                .map(GatewayCredentialResponse::of)
                .toList();
    }

    /** Turns a configured gateway off without losing its keys, for an outage or a rollback. */
    @Transactional
    public GatewayCredentialResponse setEnabled(AuthenticatedUser admin, String provider, boolean enabled) {
        String normalised = provider.toUpperCase(java.util.Locale.ROOT);

        PaymentGatewayCredential credential = credentials.findByProvider(normalised)
                .orElseThrow(() -> new NotFoundException("Payment gateway", normalised));

        credential.setEnabled(enabled);
        credential.setUpdatedByUserId(admin.id());

        PaymentGatewayCredential saved = credentials.save(credential);
        auditLog.paymentGatewayConfigured(admin.id(), normalised, saved.getMode().name(),
                saved.keyIdHint(), enabled);

        return GatewayCredentialResponse.of(saved);
    }

    /**
     * The decrypted keys, for the adapter that is about to call the gateway.
     *
     * <p>Module-internal: the gateway adapters in {@code infrastructure} call it, and it
     * is not in {@code contract}, so no other module can reach a live key at all. Empty
     * when the provider has no keys or has been switched off, which is what makes the
     * platform fall back to bank transfer instead of failing a vendor's checkout.
     */
    @Transactional(readOnly = true)
    public Optional<GatewayCredentials> usable(String provider) {
        return credentials.findByProvider(provider.toUpperCase(java.util.Locale.ROOT))
                .filter(PaymentGatewayCredential::isUsable)
                .map(this::decrypt);
    }

    private GatewayCredentials decrypt(PaymentGatewayCredential credential) {
        String webhookSecret = credential.getWebhookSecretCipher() == null
                ? null : cipher.decrypt(credential.getWebhookSecretCipher());

        return new GatewayCredentials(credential.getKeyId(),
                cipher.decrypt(credential.getKeySecretCipher()), webhookSecret, credential.getMode());
    }
}
