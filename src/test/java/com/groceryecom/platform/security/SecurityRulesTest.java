package com.groceryecom.platform.security;

import com.groceryecom.PostgresIntegrationTest;
import com.groceryecom.modules.identity.api.dto.AuthTokenResponse;
import com.groceryecom.modules.identity.api.dto.ChangePasswordRequest;
import com.groceryecom.modules.identity.application.ChangePasswordService;
import com.groceryecom.modules.identity.application.GetUserService;
import com.groceryecom.modules.identity.application.LoginService;
import com.groceryecom.modules.identity.application.RefreshTokenService;
import com.groceryecom.modules.identity.application.RegisterCustomerService;
import com.groceryecom.modules.identity.application.SessionService;
import com.groceryecom.platform.web.RequestIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * URL security rules, the error response contract and trace ids, as seen through
 * the /api context path. Application services are mocked: this test is about the
 * platform, not about business rules.
 */
@SpringBootTest
class SecurityRulesTest extends PostgresIntegrationTest {

    private static final String CONTEXT_PATH = "/api";
    private static final UUID ALICE = UUID.randomUUID();

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private RequestIdFilter requestIdFilter;

    @MockitoBean
    private LoginService loginService;

    @MockitoBean
    private RegisterCustomerService registerCustomerService;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @MockitoBean
    private GetUserService getUserService;

    @MockitoBean
    private ChangePasswordService changePasswordService;

    @MockitoBean
    private SessionService sessionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(requestIdFilter)
                .apply(springSecurity())
                .build();
    }

    @Test
    void loginIsPublic() throws Exception {
        when(loginService.execute(any(), any())).thenReturn(AuthTokenResponse.bearer("access", "refresh", 900, null));

        mockMvc.perform(json("/v1/auth/login", "{\"username\":\"alice\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access"));
    }

    @Test
    void apiDocsAndHealthArePublic() throws Exception {
        mockMvc.perform(api(get(CONTEXT_PATH + "/v3/api-docs"))).andExpect(status().isOk());
        mockMvc.perform(api(get(CONTEXT_PATH + "/actuator/health/liveness"))).andExpect(status().isOk());
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
    void protectedEndpointWithoutTokenReturnsJsonUnauthorized() throws Exception {
        mockMvc.perform(api(get(CONTEXT_PATH + "/v1/auth/me")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void accessTokenIsAccepted() throws Exception {
        mockMvc.perform(api(get(CONTEXT_PATH + "/v1/auth/me")).header("Authorization", "Bearer " + accessToken()))
                .andExpect(status().isOk());

        verify(getUserService).execute(ALICE);
    }

    @Test
    void refreshTokenIsRejectedAsAccessToken() throws Exception {
        String refreshToken = tokenProvider.createRefreshToken(ALICE).token();

        mockMvc.perform(api(get(CONTEXT_PATH + "/v1/auth/me")).header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePasswordActsOnTheLoggedInUser() throws Exception {
        mockMvc.perform(changePassword("newPassword1").header("Authorization", "Bearer " + accessToken()))
                .andExpect(status().isOk());

        verify(changePasswordService).execute(ALICE,
                new ChangePasswordRequest("oldPassword1", "newPassword1", "newPassword1"));
    }

    @Test
    void mismatchedPasswordConfirmationIsAValidationError() throws Exception {
        mockMvc.perform(changePassword("somethingElse").header("Authorization", "Bearer " + accessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.confirmPasswordMatching").value("must match newPassword"));
    }

    @Test
    void registrationValidatesInput() throws Exception {
        mockMvc.perform(json("/v1/auth/register", "{\"username\":\"a b\",\"email\":\"nope\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void malformedJsonIsABadRequestNotAServerError() throws Exception {
        mockMvc.perform(json("/v1/auth/login", "{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void unknownEndpointReturnsJsonNotFound() throws Exception {
        mockMvc.perform(api(get(CONTEXT_PATH + "/v1/does-not-exist")).header("Authorization", "Bearer " + accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void wrongHttpMethodReturnsMethodNotAllowed() throws Exception {
        mockMvc.perform(api(get(CONTEXT_PATH + "/v1/auth/change-password"))
                        .header("Authorization", "Bearer " + accessToken()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void everyResponseCarriesATraceId() throws Exception {
        mockMvc.perform(api(get(CONTEXT_PATH + "/v1/auth/me")).header(RequestIdFilter.HEADER, "lb-trace-123"))
                .andExpect(header().string(RequestIdFilter.HEADER, "lb-trace-123"))
                .andExpect(jsonPath("$.traceId").value("lb-trace-123"));

        // An unsafe incoming id is replaced, not echoed into logs and headers
        mockMvc.perform(api(get(CONTEXT_PATH + "/v1/auth/me")).header(RequestIdFilter.HEADER, "bad id\r\ninjected"))
                .andExpect(header().string(RequestIdFilter.HEADER, matchesPattern("[0-9a-f-]{36}")));
    }

    private String accessToken() {
        return tokenProvider.createAccessToken(ALICE, UUID.randomUUID(), "alice", List.of("CUSTOMER")).token();
    }

    private MockHttpServletRequestBuilder changePassword(String confirmPassword) {
        return json("/v1/auth/change-password",
                "{\"oldPassword\":\"oldPassword1\",\"newPassword\":\"newPassword1\",\"confirmPassword\":\"%s\"}"
                        .formatted(confirmPassword));
    }

    private MockHttpServletRequestBuilder json(String path, String body) {
        return api(post(CONTEXT_PATH + path)).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    /** Prefixes the context path the way a real request arrives. */
    private MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder builder) {
        return builder.contextPath(CONTEXT_PATH);
    }
}
