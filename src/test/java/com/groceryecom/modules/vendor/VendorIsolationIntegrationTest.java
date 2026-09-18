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

import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The rule the whole platform rests on: one vendor cannot see or change another
 * vendor's data by putting a different id in the URL.
 *
 * <p>With 100 stores in one database, this is the failure that ends the business, and
 * it is invisible in a test that only ever uses one account. So every case here uses
 * two real stores and a real admin, over HTTP, against a real PostgreSQL.
 */
@SpringBootTest
class VendorIsolationIntegrationTest extends PostgresIntegrationTest {

    private static final String CONTEXT_PATH = "/api";
    private static final String REASON = "Registration documents could not be verified";

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
    void aVendorCannotEditAnotherVendorsStore() throws Exception {
        Store mine = openStore();
        Store theirs = openStore();

        mockMvc.perform(patchApi("/v1/vendors/" + theirs.vendorId(), mine.accessToken())
                        .content("{\"displayName\":\"Taken Over\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    /**
     * 404 rather than 403 on purpose: a 403 would confirm that the store exists, which
     * is how an outsider maps the platform's vendors by trying ids.
     */
    @Test
    void anotherVendorsPendingStoreLooksLikeItDoesNotExist() throws Exception {
        Store mine = openStore();
        Store theirs = openStore();

        mockMvc.perform(getApi("/v1/vendors/" + theirs.vendorId(), mine.accessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void myOwnStoreIsVisibleToMeWhileItIsStillPending() throws Exception {
        Store mine = openStore();

        mockMvc.perform(getApi("/v1/vendors/me", mine.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(mine.vendorId()))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                // Its own people see the contact details
                .andExpect(jsonPath("$.data[0].contactEmail").exists());
    }

    @Test
    void theCallersOwnStoresAreNeverAnonymous() throws Exception {
        mockMvc.perform(getApi("/v1/vendors/me", null))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Two gates, and both must be open: an admin decided the store may be here, and it
     * holds a licence. Approval alone is not a storefront.
     */
    @Test
    void aStoreReachesTheStorefrontOnlyWhenItIsBothApprovedAndLicensed() throws Exception {
        Store store = openStore();

        // Anonymous, as a customer browsing
        mockMvc.perform(getApi("/v1/vendors/" + store.vendorId(), null))
                .andExpect(status().isNotFound());

        approve(store.vendorId());

        // Approved, but with no plan: still not selling
        mockMvc.perform(getApi("/v1/vendors/" + store.vendorId(), null))
                .andExpect(status().isNotFound());

        startPlan(store);

        mockMvc.perform(getApi("/v1/vendors/" + store.vendorId(), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.displayName").exists())
                // The public view leaves out business contact data
                .andExpect(jsonPath("$.data.contactEmail").doesNotExist())
                .andExpect(jsonPath("$.data.legalName").doesNotExist());
    }

    /** Approval does not widen what other vendors may do with the store. */
    @Test
    void approvalDoesNotLetAnotherVendorEditTheStore() throws Exception {
        Store mine = openStore();
        Store theirs = openStore();
        approve(theirs.vendorId());

        mockMvc.perform(patchApi("/v1/vendors/" + theirs.vendorId(), mine.accessToken())
                        .content("{\"displayName\":\"Taken Over\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void anOwnerCanEditTheirOwnStore() throws Exception {
        Store mine = openStore();

        mockMvc.perform(patchApi("/v1/vendors/" + mine.vendorId(), mine.accessToken())
                        .content("{\"displayName\":\"Fresh Mart Express\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Fresh Mart Express"));
    }

    /** Deciding about a store is an admin power, not something a vendor does for itself. */
    @Test
    void aVendorCannotApproveItsOwnStore() throws Exception {
        Store mine = openStore();

        mockMvc.perform(postApi("/v1/vendors/" + mine.vendorId() + "/approve", mine.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void aSuspendedStoreLeavesTheStorefront() throws Exception {
        Store store = openStore();
        // A plan opens the store by itself: paying is the approval
        startPlan(store);
        mockMvc.perform(getApi("/v1/vendors/" + store.vendorId(), null)).andExpect(status().isOk());

        mockMvc.perform(postApi("/v1/vendors/" + store.vendorId() + "/suspend", adminToken())
                        .content("{\"reason\":\"" + REASON + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));

        mockMvc.perform(getApi("/v1/vendors/" + store.vendorId(), null))
                .andExpect(status().isNotFound());

        // Its owner still sees it, with the reason: that is how they learn what happened
        mockMvc.perform(getApi("/v1/vendors/me", store.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("SUSPENDED"))
                .andExpect(jsonPath("$.data[0].statusReason").value(REASON));
    }

    @Test
    void oneAccountCannotOpenASecondStore() throws Exception {
        Store store = openStore();

        mockMvc.perform(postApi("/v1/vendors", store.accessToken())
                        .content(application(unique("second"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VENDOR_ALREADY_OWNED"));
    }

    @Test
    void twoStoresCannotShareAStorefrontAddress() throws Exception {
        Store first = openStore();
        String taken = slugOf(first.vendorId());

        mockMvc.perform(postApi("/v1/vendors", registerCustomer())
                        .content(application(taken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VENDOR_SLUG_EXISTS"));
    }

    // --- helpers -------------------------------------------------------------------

    private record Store(String accessToken, String vendorId) {
    }

    /** A fresh customer who applies for a store, which is then pending. */
    private Store openStore() throws Exception {
        String token = registerCustomer();
        return new Store(token, registerStore(token, unique("store")));
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

    private String registerStore(String accessToken, String slug) throws Exception {
        String body = mockMvc.perform(postApi("/v1/vendors", accessToken).content(application(slug)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(body, "$.data.id");
    }

    private void approve(String vendorId) throws Exception {
        mockMvc.perform(postApi("/v1/vendors/" + vendorId + "/approve", adminToken()))
                .andExpect(status().isOk());
    }

    /**
     * Puts the store on a plan with a free trial, which licenses it immediately. That
     * also approves a store still waiting for an admin, which is the point of it:
     * licensing is what opens a shop, and these tests would otherwise describe a
     * storefront nobody can reach.
     */
    private void startPlan(Store store) throws Exception {
        mockMvc.perform(postApi("/v1/vendors/" + store.vendorId() + "/subscription", store.accessToken())
                        .content("{\"planCode\":\"STARTER\"}"))
                .andExpect(status().isOk());
    }

    private String slugOf(String vendorId) {
        return jdbc.queryForObject("SELECT slug FROM vendors WHERE public_id = ?::uuid",
                String.class, vendorId);
    }

    /**
     * An admin account. Registration always creates a customer, deliberately, so the
     * role is set in the database the way the admin console will, and the account then
     * logs in normally to get a token that carries it.
     */
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

    private static String application(String slug) {
        return ("{\"slug\":\"%s\",\"legalName\":\"Fresh Mart Sdn Bhd\",\"displayName\":\"Fresh Mart\","
                + "\"contactEmail\":\"owner@freshmart.example\",\"contactPhone\":\"+60123456789\"}")
                .formatted(slug);
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static MockHttpServletRequestBuilder getApi(String path, String accessToken) {
        return authorise(get(CONTEXT_PATH + path).contextPath(CONTEXT_PATH), accessToken);
    }

    private static MockHttpServletRequestBuilder postApi(String path, String accessToken) {
        return authorise(post(CONTEXT_PATH + path).contextPath(CONTEXT_PATH)
                .contentType(MediaType.APPLICATION_JSON), accessToken);
    }

    private static MockHttpServletRequestBuilder patchApi(String path, String accessToken) {
        return authorise(patch(CONTEXT_PATH + path).contextPath(CONTEXT_PATH)
                .contentType(MediaType.APPLICATION_JSON), accessToken);
    }

    /** A null token means the request is anonymous, which several cases rely on. */
    private static MockHttpServletRequestBuilder authorise(MockHttpServletRequestBuilder request, String token) {
        return token == null ? request : request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
