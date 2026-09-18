package com.groceryecom.platform.security;

import com.groceryecom.PostgresIntegrationTest;
import com.groceryecom.platform.web.RequestIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The rules that hold whatever business endpoints exist.
 *
 * <p>There are no business modules in this project yet, and these still apply: anything
 * not on the public list needs a token, an access token is accepted while a refresh token
 * is not, metrics are readable only from a private network, and every response carries a
 * trace id. A module added later inherits all of it without doing anything.
 *
 * <p>A path that no controller serves is used on purpose below. Spring Security runs
 * before routing, so an unauthenticated request is refused with 401 whether or not the
 * endpoint exists - which is exactly the property being tested.
 */
@SpringBootTest
class SecurityRulesTest extends PostgresIntegrationTest {

    private static final String CONTEXT_PATH = "/api";
    private static final String PROTECTED_PATH = CONTEXT_PATH + "/v1/anything";
    private static final UUID ALICE = UUID.randomUUID();

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private RequestIdFilter requestIdFilter;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(requestIdFilter)
                .apply(springSecurity())
                .build();
    }

    @Test
    void apiDocsAndHealthArePublic() throws Exception {
        mockMvc.perform(api(get(CONTEXT_PATH + "/v3/api-docs"))).andExpect(status().isOk());
        mockMvc.perform(api(get(CONTEXT_PATH + "/actuator/health/liveness"))).andExpect(status().isOk());
    }

    @Test
    void everythingElseNeedsATokenAndSaysSoInJson() throws Exception {
        mockMvc.perform(api(get(PROTECTED_PATH)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.traceId").exists());
    }

    /** With a valid token the request gets past security and is simply not routed anywhere. */
    @Test
    void aValidAccessTokenGetsPastSecurity() throws Exception {
        mockMvc.perform(api(get(PROTECTED_PATH)).header("Authorization", "Bearer " + accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    /**
     * A refresh token is not an access token. They are both signed with the same key, so
     * only the token type inside distinguishes them - and treating one as the other would
     * hand every client a seven-day access token.
     */
    @Test
    void aRefreshTokenIsRejectedAsAnAccessToken() throws Exception {
        String refreshToken = tokenProvider.createRefreshToken(ALICE).token();

        mockMvc.perform(api(get(PROTECTED_PATH)).header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenThatIsNotOursIsRejected() throws Exception {
        mockMvc.perform(api(get(PROTECTED_PATH)).header("Authorization", "Bearer not.a.token"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Metrics describe the system, so they are not public; Prometheus cannot hold a user
     * token, so the rule is the network it scrapes from.
     */
    @Test
    void metricsAreReadableFromAPrivateNetworkOnly() throws Exception {
        mockMvc.perform(api(get(CONTEXT_PATH + "/actuator/prometheus")).with(request -> {
                    request.setRemoteAddr("10.1.2.3");
                    return request;
                }))
                .andExpect(status().isOk());

        mockMvc.perform(api(get(CONTEXT_PATH + "/actuator/prometheus")).with(request -> {
                    request.setRemoteAddr("203.0.113.9");
                    return request;
                }))
                .andExpect(status().isUnauthorized());
    }

    /**
     * A forged X-Forwarded-For must not open the metrics endpoint.
     *
     * <p>Note what this test does and does not prove: MockMvc does not run Tomcat's
     * RemoteIpValve, so it only checks that the header alone changes nothing here. The
     * real protection is {@code server.forward-headers-strategy: native} plus
     * {@code server.tomcat.remoteip.internal-proxies}, which makes Tomcat honour the
     * header only when the peer is a recognised proxy. Verify that in staging with a
     * real request through the load balancer.
     */
    @Test
    void metricsCannotBeReachedByForgingAForwardedHeader() throws Exception {
        mockMvc.perform(api(get(CONTEXT_PATH + "/actuator/prometheus"))
                        .header("X-Forwarded-For", "10.0.0.1")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.9");
                            return request;
                        }))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void everyResponseCarriesATraceIdHeader() throws Exception {
        mockMvc.perform(api(get(CONTEXT_PATH + "/actuator/health")))
                .andExpect(header().string("X-Request-Id", matchesPattern("[0-9a-f-]{36}")));
    }

    /** A client's own request id is kept, so their logs and ours line up. */
    @Test
    void aClientSuppliedRequestIdIsKept() throws Exception {
        String requestId = UUID.randomUUID().toString();

        mockMvc.perform(api(get(CONTEXT_PATH + "/actuator/health")).header("X-Request-Id", requestId))
                .andExpect(header().string("X-Request-Id", requestId));
    }

    private String accessToken() {
        return tokenProvider.createAccessToken(ALICE, UUID.randomUUID(), "alice", List.of("CUSTOMER")).token();
    }

    /** MockMvc needs telling about the context path; a real request carries it in the URL. */
    private static MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder request) {
        return request.contextPath(CONTEXT_PATH);
    }
}
