package com.groceryecom.platform.security;

import com.groceryecom.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CORS for a multi-vendor platform: many frontends must work, unknown websites must not.
 * A browser only sends the Authorization header if the preflight answer allows it, so
 * these rules decide whether every vendor storefront can call the API at all.
 */
@SpringBootTest
class CorsRulesTest extends PostgresIntegrationTest {

    private static final String CONTEXT_PATH = "/api";
    private static final String CUSTOMER_APP = "https://shop.groceryecom.com";
    private static final String VENDOR_STOREFRONT = "https://vendor-42.groceryecom.com";
    private static final String VENDOR_OWN_DOMAIN = "https://freshmart.example";
    private static final String UNKNOWN_SITE = "https://evil.example";

    @DynamicPropertySource
    static void corsProperties(DynamicPropertyRegistry registry) {
        // One wildcard for all vendor subdomains, plus one vendor on its own domain
        registry.add("app.security.cors.allowed-origin-patterns",
                () -> "http://localhost:3000,https://*.groceryecom.com," + VENDOR_OWN_DOMAIN);
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void preflightFromTheCustomerAppIsAllowedWithoutAToken() throws Exception {
        mockMvc.perform(preflight(CUSTOMER_APP, "POST", "/v1/anything"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, CUSTOMER_APP))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        org.hamcrest.Matchers.containsString("POST")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        org.hamcrest.Matchers.containsString("Authorization")));
    }

    /** The point of using patterns: a new vendor subdomain works with no config change. */
    @Test
    void preflightFromAnyVendorSubdomainIsAllowed() throws Exception {
        mockMvc.perform(preflight(VENDOR_STOREFRONT, "GET", "/v1/anything"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, VENDOR_STOREFRONT));
    }

    @Test
    void preflightFromAVendorOwnDomainListedExplicitlyIsAllowed() throws Exception {
        mockMvc.perform(preflight(VENDOR_OWN_DOMAIN, "POST", "/v1/anything"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, VENDOR_OWN_DOMAIN));
    }

    @Test
    void preflightFromAnUnknownSiteIsRejected() throws Exception {
        mockMvc.perform(preflight(UNKNOWN_SITE, "POST", "/v1/anything"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    /** A look-alike host must not match the wildcard: only one label may be replaced. */
    @Test
    void preflightFromALookAlikeDomainIsRejected() throws Exception {
        mockMvc.perform(preflight("https://groceryecom.com.evil.example", "POST", "/v1/anything"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAllowedOriginSeesTheAllowAndExposedHeadersOnARealResponse() throws Exception {
        mockMvc.perform(api(get(CONTEXT_PATH + "/actuator/health/liveness"))
                        .header(HttpHeaders.ORIGIN, VENDOR_STOREFRONT))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, VENDOR_STOREFRONT))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        org.hamcrest.Matchers.containsString("X-Request-Id")))
                // Tokens are sent in a header, not a cookie, so credentials stay off
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    private MockHttpServletRequestBuilder preflight(String origin, String method, String path) {
        return api(options(CONTEXT_PATH + path))
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Content-Type");
    }

    private MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder builder) {
        return builder.contextPath(CONTEXT_PATH);
    }
}
