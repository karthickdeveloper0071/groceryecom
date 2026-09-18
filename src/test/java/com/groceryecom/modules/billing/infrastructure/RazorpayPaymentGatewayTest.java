package com.groceryecom.modules.billing.infrastructure;

import com.groceryecom.modules.billing.application.GatewayCredentials;
import com.groceryecom.modules.billing.application.GatewayCredentialsService;
import com.groceryecom.modules.billing.application.PaymentGateway;
import com.groceryecom.modules.billing.domain.PaymentGatewayCredential.GatewayMode;
import com.groceryecom.shared.money.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * What the platform actually sends Razorpay, and what it does when Razorpay does not
 * answer. The HTTP call is stubbed; everything else is the real adapter.
 */
@ExtendWith(MockitoExtension.class)
class RazorpayPaymentGatewayTest {

    private static final UUID VENDOR = UUID.randomUUID();
    private static final String REFERENCE = "RAZORPAY-" + UUID.randomUUID();
    private static final Money PRICE = Money.ofMinor(14900, "INR");

    @Mock
    private GatewayCredentialsService credentials;

    private MockRestServiceServer razorpay;
    private RazorpayPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        razorpay = MockRestServiceServer.bindTo(builder).build();
        gateway = new RazorpayPaymentGateway(credentials, builder, new RazorpayProperties("https://razorpay.test"));
    }

    @Test
    void createsAnOrderForTheExactAmountAndReturnsItsIdAsTheReference() {
        givenKeysInstalled();
        razorpay.expect(requestTo("https://razorpay.test/v1/orders"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                // Basic auth with the key id and secret, which is how Razorpay authenticates us
                .andExpect(header(HttpHeaders.AUTHORIZATION, basicAuth("rzp_test_key", "secret_value")))
                // The amount goes in minor units, exactly as Money holds it: no conversion
                .andExpect(jsonPath("$.amount").value(14900))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.receipt").value(REFERENCE))
                // Without automatic capture the money is authorised and never taken, which
                // looks exactly like a paid vendor who has not paid
                .andExpect(jsonPath("$.payment_capture").value(1))
                .andExpect(jsonPath("$.notes.vendorId").value(VENDOR.toString()))
                .andRespond(withSuccess("""
                        {"id":"order_NpQrSt123","entity":"order","amount":14900,"status":"created"}""",
                        MediaType.APPLICATION_JSON));

        PaymentGateway.PaymentInstruction instruction = gateway.requestPayment(VENDOR, PRICE, REFERENCE);

        // Razorpay's order id wins over ours: it is what the webhook will quote
        assertThat(instruction.reference()).isEqualTo("order_NpQrSt123");
        // The browser needs the public key to open Checkout; the secret never leaves here
        assertThat(instruction.publicKey()).isEqualTo("rzp_test_key");
        assertThat(instruction.instructions()).contains("149.00 INR");
        razorpay.verify();
    }

    /** Razorpay's own error text can name the account or the key, so the vendor never sees it. */
    @Test
    void aFailureAtRazorpayBecomesA503WithNothingLeakedFromIt() {
        givenKeysInstalled();
        razorpay.expect(requestTo("https://razorpay.test/v1/orders"))
                .andRespond(withServerError().body("{\"error\":{\"description\":\"account rzp_acc_9 is blocked\"}}"));

        assertThatThrownBy(() -> gateway.requestPayment(VENDOR, PRICE, REFERENCE))
                .isInstanceOf(RazorpayPaymentGateway.GatewayUnavailableException.class)
                .hasMessage("We could not start the payment. Try again in a moment.")
                .extracting(e -> ((RazorpayPaymentGateway.GatewayUnavailableException) e).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
    }

    @Test
    void withNoKeysInstalledTheGatewayIsNotUsed() {
        when(credentials.usable("RAZORPAY")).thenReturn(Optional.empty());

        assertThat(gateway.isConfigured()).isFalse();
        assertThatThrownBy(() -> gateway.requestPayment(VENDOR, PRICE, REFERENCE))
                .isInstanceOf(RazorpayPaymentGateway.GatewayUnavailableException.class);
    }

    private void givenKeysInstalled() {
        when(credentials.usable("RAZORPAY")).thenReturn(Optional.of(
                new GatewayCredentials("rzp_test_key", "secret_value", "whsec", GatewayMode.TEST)));
    }

    private static String basicAuth(String keyId, String secret) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((keyId + ":" + secret).getBytes(StandardCharsets.UTF_8));
    }
}
