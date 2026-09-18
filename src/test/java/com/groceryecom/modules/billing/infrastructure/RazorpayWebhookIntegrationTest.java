package com.groceryecom.modules.billing.infrastructure;

import com.groceryecom.PostgresIntegrationTest;
import com.groceryecom.modules.billing.application.PaymentGateway;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Installing Razorpay keys from the admin console, and Razorpay then paying for a store.
 *
 * <p>The outbound call to Razorpay is mocked - creating real orders in a build is not a
 * test, it is a bill. Everything else is real: the keys are encrypted and stored, the
 * gateway is chosen from them, the webhook signature is verified against the stored
 * secret, and the store ends up licensed.
 */
@SpringBootTest
class RazorpayWebhookIntegrationTest extends PostgresIntegrationTest {

    private static final String CONTEXT_PATH = "/api";
    private static final String WEBHOOK_SECRET = "whsec_from_razorpay_dashboard";
    private static final String KEY_SECRET = "rzp_secret_never_leaves_the_server";
    private static final String ORDER_ID = "order_TestOrder123";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    /** Razorpay itself. Everything up to the HTTP call is the real code path. */
    @MockitoBean
    private RazorpayPaymentGateway razorpay;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        when(razorpay.provider()).thenReturn("RAZORPAY");
        when(razorpay.isConfigured()).thenReturn(true);
        // A fresh order id per checkout, as Razorpay issues: two stores never share one
        when(razorpay.requestPayment(any(), any(), any())).thenAnswer(call ->
                new PaymentGateway.PaymentInstruction(
                        ORDER_ID + "_" + UUID.randomUUID().toString().substring(0, 8),
                        "Pay 149.00 MYR", null, "rzp_test_key"));
    }

    @Test
    void anAdminInstallsTheKeysAndTheyAreStoredEncryptedAndNeverReturned() throws Exception {
        String admin = adminToken();

        mockMvc.perform(putApi("/v1/admin/payment-gateways/razorpay", admin).content(keysJson()))
                .andExpect(status().isOk())
                // What the console may see: which key, in which mode, and nothing else
                .andExpect(jsonPath("$.data.provider").value("RAZORPAY"))
                .andExpect(jsonPath("$.data.mode").value("TEST"))
                .andExpect(jsonPath("$.data.keyIdHint").value("****_key"))
                .andExpect(jsonPath("$.data.webhooksVerified").value(true))
                .andExpect(jsonPath("$.data.keySecret").doesNotExist())
                .andExpect(jsonPath("$.data.webhookSecret").doesNotExist());

        String listed = mockMvc.perform(getApi("/v1/admin/payment-gateways", admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(listed)
                .as("no API response may carry a live gateway secret")
                .doesNotContain(KEY_SECRET)
                .doesNotContain(WEBHOOK_SECRET);

        String stored = jdbc.queryForObject(
                "SELECT key_secret_cipher FROM payment_gateway_credentials WHERE provider = 'RAZORPAY'",
                String.class);
        assertThat(stored)
                .as("a database dump must not contain a live key")
                .isNotNull()
                .doesNotContain(KEY_SECRET);
    }

    @Test
    void onlyAnAdminCanChangeThePaymentConfiguration() throws Exception {
        String vendorOwner = registerCustomer();

        mockMvc.perform(putApi("/v1/admin/payment-gateways/razorpay", vendorOwner).content(keysJson()))
                .andExpect(status().isForbidden());

        mockMvc.perform(getApi("/v1/admin/payment-gateways", null))
                .andExpect(status().isUnauthorized());
    }

    /** The whole path: keys installed, vendor pays at Razorpay, Razorpay tells us, store opens. */
    @Test
    void aSignedWebhookLicensesTheStore() throws Exception {
        installKeys();
        Store store = applyForStore();
        String orderId = subscribeToGrowth(store);

        // The store cannot sell yet: nothing has been paid
        mockMvc.perform(getApi("/v1/vendors/" + store.vendorId(), null))
                .andExpect(status().isNotFound());

        String body = orderPaidEvent(orderId);
        mockMvc.perform(webhook(body, RazorpaySignatureTest.sign(body, WEBHOOK_SECRET)))
                .andExpect(status().isOk());

        mockMvc.perform(getApi("/v1/vendors/" + store.vendorId() + "/subscription", store.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.trading").value(true));

        // And it is on the storefront, with no admin having touched it
        mockMvc.perform(getApi("/v1/vendors/" + store.vendorId(), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    /**
     * The endpoint is public, so this is the test that matters: without a valid signature
     * anybody could grant themselves a licence by posting JSON.
     */
    @Test
    void anUnsignedOrWronglySignedWebhookGrantsNothing() throws Exception {
        installKeys();
        Store store = applyForStore();
        String body = orderPaidEvent(subscribeToGrowth(store));

        mockMvc.perform(webhook(body, null))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(webhook(body, RazorpaySignatureTest.sign(body, "attackers-guess")))
                .andExpect(status().isUnauthorized());

        // A body altered after signing must not pass either
        String signature = RazorpaySignatureTest.sign(body, WEBHOOK_SECRET);
        mockMvc.perform(webhook(orderPaidEvent("order_SomeoneElse"), signature))
                .andExpect(status().isUnauthorized());

        assertThat(statusOf(store)).isEqualTo("PENDING_PAYMENT");
    }

    /** Razorpay retries what it thinks failed. A licence must not grow a month per retry. */
    @Test
    void theSameWebhookDeliveredTwiceDoesNotBuyASecondMonth() throws Exception {
        installKeys();
        Store store = applyForStore();
        String body = orderPaidEvent(subscribeToGrowth(store));
        String signature = RazorpaySignatureTest.sign(body, WEBHOOK_SECRET);

        mockMvc.perform(webhook(body, signature)).andExpect(status().isOk());
        String firstEnd = periodEnd(store);

        mockMvc.perform(webhook(body, signature)).andExpect(status().isOk());

        assertThat(periodEnd(store)).isEqualTo(firstEnd);
    }

    /** An event we do not act on is acknowledged, not retried forever. */
    @Test
    void anEventThePlatformDoesNotCareAboutIsAccepted() throws Exception {
        installKeys();

        String body = """
                {"event":"payment.authorized","payload":{"payment":{"entity":{"id":"pay_1"}}}}""";
        mockMvc.perform(webhook(body, RazorpaySignatureTest.sign(body, WEBHOOK_SECRET)))
                .andExpect(status().isOk());
    }

    /** With no webhook secret stored, nothing can be verified, so nothing is believed. */
    @Test
    void withoutAWebhookSecretEveryCallbackIsRefused() throws Exception {
        // One credentials row exists per provider and the tests in this class share a
        // database, so this case has to start from "nothing installed" explicitly
        jdbc.update("DELETE FROM payment_gateway_credentials WHERE provider = 'RAZORPAY'");

        String body = orderPaidEvent(ORDER_ID);

        mockMvc.perform(webhook(body, RazorpaySignatureTest.sign(body, WEBHOOK_SECRET)))
                .andExpect(status().isUnauthorized());
    }

    // --- helpers -------------------------------------------------------------------

    private record Store(String token, String vendorId) {
    }

    private void installKeys() throws Exception {
        mockMvc.perform(putApi("/v1/admin/payment-gateways/razorpay", adminToken()).content(keysJson()))
                .andExpect(status().isOk());
    }

    private static String keysJson() {
        return """
                {"mode":"TEST","keyId":"rzp_test_key","keySecret":"%s","webhookSecret":"%s"}"""
                .formatted(KEY_SECRET, WEBHOOK_SECRET);
    }

    private static String orderPaidEvent(String orderId) {
        return """
                {"event":"order.paid","payload":{"order":{"entity":{"id":"%s","status":"paid"}}}}"""
                .formatted(orderId);
    }

    private Store applyForStore() throws Exception {
        String token = registerCustomer();
        String slug = "store-" + UUID.randomUUID().toString().substring(0, 8);

        String body = mockMvc.perform(postApi("/v1/vendors", token).content(
                        ("{\"slug\":\"%s\",\"legalName\":\"Fresh Mart Sdn Bhd\",\"displayName\":\"Fresh Mart\","
                                + "\"contactEmail\":\"owner@freshmart.example\"}").formatted(slug)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return new Store(token, JsonPath.read(body, "$.data.id"));
    }

    /** @return the Razorpay order id the webhook will quote for this store */
    private String subscribeToGrowth(Store store) throws Exception {
        String body = mockMvc.perform(postApi("/v1/vendors/" + store.vendorId() + "/subscription", store.token())
                        .content("{\"planCode\":\"GROWTH\"}"))
                .andExpect(status().isOk())
                // The frontend opens Razorpay Checkout with these two values
                .andExpect(jsonPath("$.data.payment.reference").value(org.hamcrest.Matchers.startsWith(ORDER_ID)))
                .andExpect(jsonPath("$.data.payment.publicKey").value("rzp_test_key"))
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(body, "$.data.payment.reference");
    }

    private String statusOf(Store store) {
        return jdbc.queryForObject(
                "SELECT status FROM vendor_subscriptions WHERE vendor_public_id = ?::uuid",
                String.class, store.vendorId());
    }

    private String periodEnd(Store store) {
        return jdbc.queryForObject(
                "SELECT current_period_end::text FROM vendor_subscriptions WHERE vendor_public_id = ?::uuid",
                String.class, store.vendorId());
    }

    private String registerCustomer() throws Exception {
        String username = "user" + UUID.randomUUID().toString().substring(0, 8);
        String body = mockMvc.perform(postApi("/v1/auth/register", null)
                        .content("{\"username\":\"%s\",\"email\":\"%s@example.com\",\"password\":\"password123\"}"
                                .formatted(username, username)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(body, "$.data.accessToken");
    }

    private String adminToken() throws Exception {
        String username = "admin" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(postApi("/v1/auth/register", null)
                        .content("{\"username\":\"%s\",\"email\":\"%s@example.com\",\"password\":\"password123\"}"
                                .formatted(username, username)))
                .andExpect(status().isCreated());

        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE username = ?", username);

        String body = mockMvc.perform(postApi("/v1/auth/login", null)
                        .content("{\"username\":\"%s\",\"password\":\"password123\"}".formatted(username)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(body, "$.data.accessToken");
    }

    private static MockHttpServletRequestBuilder webhook(String body, String signature) {
        MockHttpServletRequestBuilder request = post(CONTEXT_PATH + "/v1/billing/webhooks/razorpay")
                .contextPath(CONTEXT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);

        return signature == null ? request : request.header("X-Razorpay-Signature", signature);
    }

    private static MockHttpServletRequestBuilder getApi(String path, String token) {
        return authorise(get(CONTEXT_PATH + path).contextPath(CONTEXT_PATH), token);
    }

    private static MockHttpServletRequestBuilder postApi(String path, String token) {
        return authorise(post(CONTEXT_PATH + path).contextPath(CONTEXT_PATH)
                .contentType(MediaType.APPLICATION_JSON), token);
    }

    private static MockHttpServletRequestBuilder putApi(String path, String token) {
        return authorise(put(CONTEXT_PATH + path).contextPath(CONTEXT_PATH)
                .contentType(MediaType.APPLICATION_JSON), token);
    }

    private static MockHttpServletRequestBuilder authorise(MockHttpServletRequestBuilder request, String token) {
        return token == null ? request : request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
