package com.groceryecom.modules.billing.infrastructure;

import com.groceryecom.modules.billing.application.GatewayCredentials;
import com.groceryecom.modules.billing.application.GatewayCredentialsService;
import com.groceryecom.modules.billing.application.VendorPayoutGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Registers a vendor's bank account with Razorpay Route, so Razorpay can pay the vendor
 * their share of a customer's order directly.
 *
 * <p>Two calls, in order: create a linked account for the store, then attach the bank
 * account to it as a settlement destination. Razorpay verifies the account itself, which
 * is the point of using it - the platform never sees or keeps the account number, and a
 * wrong account is caught by the party that can actually check.
 *
 * <p>With no Razorpay keys installed, this returns {@code PENDING} rather than failing.
 * A vendor can then set up their payout details before the platform owner has finished
 * signing up with Razorpay, and the destination is registered when the keys arrive.
 */
@Slf4j
@Component
class RazorpayPayoutGateway implements VendorPayoutGateway {

    private static final String ACCOUNTS_URL = "/v2/accounts";

    private final GatewayCredentialsService credentials;
    private final RestClient restClient;

    @Autowired
    RazorpayPayoutGateway(GatewayCredentialsService credentials, RazorpayProperties properties) {
        this(credentials, RestClient.builder(), properties);
    }

    /** Takes a builder so a test can bind a stub to it instead of calling Razorpay. */
    RazorpayPayoutGateway(GatewayCredentialsService credentials, RestClient.Builder restClientBuilder,
                          RazorpayProperties properties) {
        this.credentials = credentials;
        this.restClient = restClientBuilder.baseUrl(properties.apiUrl()).build();
    }

    @Override
    public String provider() {
        return RazorpayPaymentGateway.PROVIDER;
    }

    @Override
    public Registration registerDestination(UUID vendorId, String accountHolderName, String accountNumber,
                                            String ifscCode, String contactEmail) {
        Optional<GatewayCredentials> keys = credentials.usable(RazorpayPaymentGateway.PROVIDER);

        if (keys.isEmpty()) {
            // Nothing to register with yet. The vendor's details are accepted and their
            // share is held, rather than turning "we have not signed up yet" into their
            // problem.
            log.info("Payout destination for vendor {} recorded; Razorpay is not configured yet", vendorId);
            return Registration.pending(null);
        }

        try {
            Map<String, Object> account = createLinkedAccount(keys.get(), vendorId, accountHolderName,
                    accountNumber, ifscCode, contactEmail);
            String accountId = String.valueOf(account.get("id"));

            log.info("Razorpay linked account {} created for vendor {}", accountId, vendorId);
            // Razorpay activates a linked account after its own checks, so the money is
            // held until it says so rather than assumed to be deliverable
            return Registration.pending(accountId);
        } catch (RuntimeException e) {
            // Razorpay's message can name the account holder or the bank; logged, never returned
            log.error("Razorpay refused the payout destination for vendor {}: {}", vendorId, e.getMessage());
            return Registration.rejected(
                    "The bank details could not be verified. Check the account number and IFSC code.");
        }
    }

    private Map<String, Object> createLinkedAccount(GatewayCredentials keys, UUID vendorId,
                                                    String accountHolderName, String accountNumber,
                                                    String ifscCode, String contactEmail) {
        Map<String, Object> request = Map.of(
                "email", contactEmail,
                "phone", "",
                "type", "route",
                "legal_business_name", accountHolderName,
                "business_type", "partnership",
                "reference_id", vendorId.toString(),
                "notes", Map.of("vendorId", vendorId.toString()),
                "bank_account", Map.of(
                        "name", accountHolderName,
                        "ifsc", ifscCode,
                        "account_number", accountNumber));

        return restClient.post()
                .uri(ACCOUNTS_URL)
                .header(HttpHeaders.AUTHORIZATION, basicAuth(keys))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(Map.class);
    }

    private static String basicAuth(GatewayCredentials keys) {
        String value = keys.keyId() + ":" + keys.keySecret();
        return "Basic " + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
