package com.groceryecom.modules.billing;

import com.groceryecom.PostgresIntegrationTest;
import com.groceryecom.modules.billing.application.SubscriptionExpiryService;
import com.groceryecom.modules.billing.contract.VendorEntitlements;
import com.groceryecom.shared.exception.PaymentRequiredException;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Buying a licence, using it, and losing it.
 *
 * <p>The whole point of the module in one file: a store that pays starts selling without
 * anybody clicking approve, a store whose plan runs out stops selling and is told why,
 * and a store that pays again comes back.
 */
@SpringBootTest
class VendorLicensingIntegrationTest extends PostgresIntegrationTest {

    private static final String CONTEXT_PATH = "/api";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SubscriptionExpiryService expiryService;

    @Autowired
    private VendorEntitlements entitlements;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void thePriceListIsPublic() throws Exception {
        mockMvc.perform(getApi("/v1/plans", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("STARTER"))
                .andExpect(jsonPath("$.data[0].price").value(49.00))
                .andExpect(jsonPath("$.data[0].currency").value("MYR"))
                .andExpect(jsonPath("$.data[0].trialDays").value(14))
                .andExpect(jsonPath("$.data[2].code").value("SCALE"));
    }

    /**
     * The sign-up a vendor expects: choose the plan, start selling. No admin, no waiting,
     * no email asking to be approved.
     */
    @Test
    void aTrialPlanOpensTheStoreImmediatelyWithNoAdminAndNothingToPay() throws Exception {
        Store store = applyForStore();

        mockMvc.perform(postApi("/v1/vendors/" + store.vendorId() + "/subscription", store.token())
                        .content("{\"planCode\":\"STARTER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscription.status").value("TRIALING"))
                .andExpect(jsonPath("$.data.subscription.trading").value(true))
                // Nothing owed yet, so no payment instructions are sent
                .andExpect(jsonPath("$.data.payment").doesNotExist())
                .andExpect(jsonPath("$.data.subscription.message").value(
                        org.hamcrest.Matchers.containsString("free trial")));

        // The store was PENDING a minute ago and is now trading, without an admin
        mockMvc.perform(getApi("/v1/vendors/" + store.vendorId(), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    void aPaidPlanOnlyOpensTheStoreWhenTheMoneyArrives() throws Exception {
        Store store = applyForStore();

        String checkout = mockMvc.perform(postApi("/v1/vendors/" + store.vendorId() + "/subscription", store.token())
                        .content("{\"planCode\":\"GROWTH\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscription.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.subscription.trading").value(false))
                .andExpect(jsonPath("$.data.payment.amount").value(149.00))
                .andExpect(jsonPath("$.data.payment.provider").value("MANUAL"))
                .andExpect(jsonPath("$.data.payment.instructions").exists())
                .andReturn().getResponse().getContentAsString();

        // Not on the storefront yet: a store that has not paid is not open
        mockMvc.perform(getApi("/v1/vendors/" + store.vendorId(), null))
                .andExpect(status().isNotFound());

        String reference = JsonPath.read(checkout, "$.data.payment.reference");
        mockMvc.perform(postApi("/v1/billing/payments/" + reference + "/confirm", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.message").value(
                        org.hamcrest.Matchers.containsString("renews on")));

        mockMvc.perform(getApi("/v1/vendors/" + store.vendorId(), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    /**
     * An admin clicking twice, or a gateway retrying a webhook, must not buy the store a
     * second month.
     */
    @Test
    void confirmingTheSamePaymentTwiceChangesNothing() throws Exception {
        Store store = applyForStore();
        String reference = subscribeAndGetReference(store, "GROWTH");
        String admin = adminToken();

        String first = mockMvc.perform(postApi("/v1/billing/payments/" + reference + "/confirm", admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String second = mockMvc.perform(postApi("/v1/billing/payments/" + reference + "/confirm", admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Compared to the second, not to the nanosecond: the first answer comes from the
        // object in memory and the second from PostgreSQL, which stores microseconds. The
        // claim is that no second period was added, not that the two strings match.
        assertThat(periodEndSecond(second))
                .as("a repeated confirmation must not extend the licence")
                .isEqualTo(periodEndSecond(first));
    }

    @Test
    void aStoreWhosePlanRanOutStopsSellingAndIsToldWhy() throws Exception {
        Store store = tradingStore();

        // The licence and its grace days ran out yesterday
        expirePlan(store.vendorId(), Instant.now().minus(2, ChronoUnit.DAYS));
        assertThat(expiryService.settleDueSubscriptions(Instant.now())).isEqualTo(1);

        // Gone from the storefront
        mockMvc.perform(getApi("/v1/vendors/" + store.vendorId(), null))
                .andExpect(status().isNotFound());

        // And the vendor is told, in words, what happened and what to do
        mockMvc.perform(getApi("/v1/vendors/" + store.vendorId() + "/subscription", store.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EXPIRED"))
                .andExpect(jsonPath("$.data.trading").value(false))
                .andExpect(jsonPath("$.data.daysLeft").value(0))
                .andExpect(jsonPath("$.data.message").value(
                        org.hamcrest.Matchers.containsString("expired on")));

        // The store's own view agrees, so a dashboard shows it without asking twice
        mockMvc.perform(getApi("/v1/vendors/me", store.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].canSell").value(false));
    }

    /**
     * What every module built after this one will do before letting a store act: ask
     * billing, and get a 402 with a date rather than a bare "forbidden".
     */
    @Test
    void anExpiredStoreIsRefusedBySellingActionsWithAMessageAndADate() throws Exception {
        Store store = tradingStore();
        expirePlan(store.vendorId(), Instant.now().minus(2, ChronoUnit.DAYS));
        expiryService.settleDueSubscriptions(Instant.now());

        UUID vendorId = UUID.fromString(store.vendorId());
        assertThatThrownBy(() -> entitlements.requireTrading(vendorId))
                .isInstanceOf(PaymentRequiredException.class)
                .hasMessageContaining("expired on")
                .hasMessageContaining("Renew")
                .extracting(thrown -> ((PaymentRequiredException) thrown).getErrorCode())
                .isEqualTo("SUBSCRIPTION_EXPIRED");
    }

    /** A late payer keeps selling through the grace days, and is warned rather than cut off. */
    @Test
    void aMissedPaymentLeavesTheStoreSellingUntilGraceRunsOut() throws Exception {
        Store store = tradingStore();

        // The period ended yesterday; grace has not
        Instant yesterday = Instant.now().minus(1, ChronoUnit.DAYS);
        movePeriod(store.vendorId(), yesterday.minus(30, ChronoUnit.DAYS), yesterday,
                Instant.now().plus(2, ChronoUnit.DAYS));

        assertThat(expiryService.settleDueSubscriptions(Instant.now())).isEqualTo(1);

        mockMvc.perform(getApi("/v1/vendors/" + store.vendorId() + "/subscription", store.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAST_DUE"))
                .andExpect(jsonPath("$.data.trading").value(true))
                .andExpect(jsonPath("$.data.message").value(
                        org.hamcrest.Matchers.containsString("stays open until")));

        // Still on the storefront: the store does not know anything is wrong, and should not
        mockMvc.perform(getApi("/v1/vendors/" + store.vendorId(), null)).andExpect(status().isOk());
    }

    @Test
    void payingAgainPutsAnExpiredStoreBackOnTheStorefront() throws Exception {
        Store store = tradingStore();
        expirePlan(store.vendorId(), Instant.now().minus(2, ChronoUnit.DAYS));
        expiryService.settleDueSubscriptions(Instant.now());

        String reference = subscribeAndGetReference(store, "GROWTH");
        mockMvc.perform(postApi("/v1/billing/payments/" + reference + "/confirm", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(getApi("/v1/vendors/" + store.vendorId(), null))
                .andExpect(status().isOk());
    }

    @Test
    void theExpiryPassIsSafeToRunAgainAndAgain() throws Exception {
        Store store = tradingStore();
        expirePlan(store.vendorId(), Instant.now().minus(2, ChronoUnit.DAYS));

        assertThat(expiryService.settleDueSubscriptions(Instant.now())).isEqualTo(1);
        assertThat(expiryService.settleDueSubscriptions(Instant.now()))
                .as("a second pass, or a second instance running one, must find nothing left to do")
                .isZero();
    }

    /** A store's licence is its owner's business: another vendor cannot even look. */
    @Test
    void anotherVendorCannotSeeOrChangeThisStoresPlan() throws Exception {
        Store mine = tradingStore();
        Store theirs = applyForStore();

        mockMvc.perform(getApi("/v1/vendors/" + mine.vendorId() + "/subscription", theirs.token()))
                .andExpect(status().isNotFound());

        mockMvc.perform(postApi("/v1/vendors/" + mine.vendorId() + "/subscription", theirs.token())
                        .content("{\"planCode\":\"SCALE\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyAnAdminCanConfirmThatMoneyArrived() throws Exception {
        Store store = applyForStore();
        String reference = subscribeAndGetReference(store, "GROWTH");

        mockMvc.perform(postApi("/v1/billing/payments/" + reference + "/confirm", store.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void aCancelledPlanKeepsTheStoreSellingUntilTheEndOfThePaidPeriod() throws Exception {
        Store store = tradingStore();

        mockMvc.perform(postApi("/v1/vendors/" + store.vendorId() + "/subscription/cancel", store.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trading").value(true))
                .andExpect(jsonPath("$.data.cancelledAt").exists())
                .andExpect(jsonPath("$.data.message").value(
                        org.hamcrest.Matchers.containsString("keeps selling until")));

        mockMvc.perform(getApi("/v1/vendors/" + store.vendorId(), null)).andExpect(status().isOk());
    }

    // --- helpers -------------------------------------------------------------------

    private record Store(String token, String vendorId) {
    }

    private static Instant periodEndSecond(String responseBody) {
        return Instant.parse(JsonPath.read(responseBody, "$.data.periodEnd"))
                .truncatedTo(ChronoUnit.SECONDS);
    }

    /** A store that has applied and is waiting: no plan, not selling. */
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

    /** A store that paid for a plan and is selling. */
    private Store tradingStore() throws Exception {
        Store store = applyForStore();
        String reference = subscribeAndGetReference(store, "GROWTH");

        mockMvc.perform(postApi("/v1/billing/payments/" + reference + "/confirm", adminToken()))
                .andExpect(status().isOk());
        return store;
    }

    private String subscribeAndGetReference(Store store, String planCode) throws Exception {
        String body = mockMvc.perform(postApi("/v1/vendors/" + store.vendorId() + "/subscription", store.token())
                        .content("{\"planCode\":\"%s\"}".formatted(planCode)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(body, "$.data.payment.reference");
    }

    /**
     * Moves a licence into the past, which is the one thing a test cannot wait for. The
     * period start moves with it, because the database refuses a period that ends before
     * it begins.
     */
    private void expirePlan(String vendorId, Instant when) {
        movePeriod(vendorId, when.minus(30, ChronoUnit.DAYS), when, when);
    }

    private void movePeriod(String vendorId, Instant start, Instant end, Instant graceUntil) {
        jdbc.update("""
                UPDATE vendor_subscriptions
                   SET current_period_start = ?, current_period_end = ?, grace_until = ?
                 WHERE vendor_public_id = ?::uuid
                """,
                java.sql.Timestamp.from(start), java.sql.Timestamp.from(end),
                java.sql.Timestamp.from(graceUntil), vendorId);
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

    private static MockHttpServletRequestBuilder getApi(String path, String token) {
        return authorise(get(CONTEXT_PATH + path).contextPath(CONTEXT_PATH), token);
    }

    private static MockHttpServletRequestBuilder postApi(String path, String token) {
        return authorise(post(CONTEXT_PATH + path).contextPath(CONTEXT_PATH)
                .contentType(MediaType.APPLICATION_JSON), token);
    }

    private static MockHttpServletRequestBuilder authorise(MockHttpServletRequestBuilder request, String token) {
        return token == null ? request : request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
