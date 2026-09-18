package com.groceryecom.modules.vendor;

import com.groceryecom.PostgresIntegrationTest;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The lists the admin console is built on.
 *
 * <p>Until these existed, an admin could approve a store only by reading its id out of
 * the database: every other admin endpoint acts on one store already known by id. The
 * case that matters most here is the first one - a store that applied appears in the
 * queue, and can then be approved.
 */
@SpringBootTest
class AdminConsoleIntegrationTest extends PostgresIntegrationTest {

    private static final String CONTEXT_PATH = "/api";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void aStoreThatAppliedAppearsInTheApprovalQueueAndCanThenBeApproved() throws Exception {
        Store store = applyForStore();
        String admin = adminToken();

        String queue = mockMvc.perform(getApi("/v1/admin/vendors?status=PENDING&size=100", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andReturn().getResponse().getContentAsString();

        List<String> ids = JsonPath.read(queue, "$.data.items[*].id");
        assertThat(ids).contains(store.vendorId());

        // The whole point of the list: an id taken from it works on the action endpoints
        mockMvc.perform(postApi("/v1/vendors/" + store.vendorId() + "/approve", admin))
                .andExpect(status().isOk());
    }

    @Test
    void theQueueShowsWhatAnAdminNeedsToDecide() throws Exception {
        Store store = applyForStore();

        mockMvc.perform(getApi("/v1/admin/vendors?status=PENDING&size=100", adminToken()))
                .andExpect(status().isOk())
                // An admin sees the contact details; the public storefront view does not
                .andExpect(jsonPath("$.data.items[?(@.id == '" + store.vendorId() + "')].contactEmail")
                        .exists())
                .andExpect(jsonPath("$.data.items[?(@.id == '" + store.vendorId() + "')].legalName")
                        .exists());
    }

    @Test
    void searchFindsAStoreByNameOrAddress() throws Exception {
        Store store = applyForStore();
        String slug = jdbc.queryForObject("SELECT slug FROM vendors WHERE public_id = ?::uuid",
                String.class, store.vendorId());

        mockMvc.perform(getApi("/v1/admin/vendors?search=" + slug, adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(store.vendorId()));
    }

    /** A caller cannot ask for a million rows and stall the database for everybody else. */
    @Test
    void anAbsurdPageSizeIsClampedRatherThanObeyed() throws Exception {
        applyForStore();

        mockMvc.perform(getApi("/v1/admin/vendors?size=100000", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100));
    }

    @Test
    void theListIsPagedAndSaysWhetherThereIsMore() throws Exception {
        applyForStore();
        applyForStore();

        mockMvc.perform(getApi("/v1/admin/vendors?status=PENDING&page=0&size=1", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.hasNext").value(true));
    }

    @Test
    void pendingApplicationsComeOldestFirstBecauseItIsAQueue() throws Exception {
        Store first = applyForStore();
        Store second = applyForStore();

        String queue = mockMvc.perform(getApi("/v1/admin/vendors?status=PENDING&size=100", adminToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> ids = JsonPath.read(queue, "$.data.items[*].id");
        assertThat(ids.indexOf(first.vendorId())).isLessThan(ids.indexOf(second.vendorId()));
    }

    @Test
    void aBankTransferWaitingForConfirmationIsInThePaymentsQueue() throws Exception {
        Store store = applyForStore();
        String reference = subscribeAndGetReference(store);

        String queue = mockMvc.perform(getApi("/v1/admin/payments?status=PENDING&size=100", adminToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> references = JsonPath.read(queue, "$.data.items[*].reference");
        assertThat(references).contains(reference);

        // The reference from the list is what confirms the payment
        mockMvc.perform(postApi("/v1/billing/payments/" + reference + "/confirm", adminToken()))
                .andExpect(status().isOk());
    }

    @Test
    void licencesCanBeListedAndFilteredByState() throws Exception {
        Store store = applyForStore();
        subscribeAndGetReference(store);

        mockMvc.perform(getApi("/v1/admin/subscriptions?status=PENDING_PAYMENT&size=100", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.vendorId == '" + store.vendorId() + "')].planCode")
                        .value(org.hamcrest.Matchers.hasItem("GROWTH")));
    }

    @Test
    void anAccountCanBeFoundByEmailForASupportCall() throws Exception {
        String username = registerNamedCustomer();

        mockMvc.perform(getApi("/v1/admin/users?search=" + username, adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].username").value(username))
                // Never anything that could be used to sign in as them
                .andExpect(jsonPath("$.data.items[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].password").doesNotExist());
    }

    /** Every admin list, refused to everybody else. */
    @Test
    void theAdminListsAreAdminOnly() throws Exception {
        String vendorOwner = registerCustomer();

        for (String path : List.of("/v1/admin/vendors", "/v1/admin/subscriptions",
                "/v1/admin/payments", "/v1/admin/users")) {
            mockMvc.perform(getApi(path, vendorOwner))
                    .andExpect(status().isForbidden());
            mockMvc.perform(getApi(path, null))
                    .andExpect(status().isUnauthorized());
        }
    }

    // --- helpers -------------------------------------------------------------------

    private record Store(String token, String vendorId) {
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

    private String subscribeAndGetReference(Store store) throws Exception {
        String body = mockMvc.perform(postApi("/v1/vendors/" + store.vendorId() + "/subscription",
                        store.token()).content("{\"planCode\":\"GROWTH\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(body, "$.data.payment.reference");
    }

    private String registerCustomer() throws Exception {
        return tokenFor(registerNamedCustomer());
    }

    private String registerNamedCustomer() throws Exception {
        String username = "user" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(postApi("/v1/auth/register", null)
                        .content("{\"username\":\"%s\",\"email\":\"%s@example.com\",\"password\":\"password123\"}"
                                .formatted(username, username)))
                .andExpect(status().isCreated());
        return username;
    }

    private String tokenFor(String username) throws Exception {
        String body = mockMvc.perform(postApi("/v1/auth/login", null)
                        .content("{\"username\":\"%s\",\"password\":\"password123\"}".formatted(username)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(body, "$.data.accessToken");
    }

    private String adminToken() throws Exception {
        String username = registerNamedCustomer();
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE username = ?", username);
        return tokenFor(username);
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
