package com.groceryecom.platform.security;

import com.groceryecom.PostgresIntegrationTest;
import com.groceryecom.modules.identity.internal.AuthService;
import com.groceryecom.modules.identity.web.dto.AuthTokenResponse;
import com.groceryecom.modules.identity.web.dto.ChangePasswordRequest;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * URL security rules, error bodies and request ids, as seen through the /api context path.
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
    private AuthService authService;

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
        when(authService.login(any())).thenReturn(AuthTokenResponse.bearer("access", "refresh", 900, null));

        mockMvc.perform(json(post(CONTEXT_PATH + "/v1/auth/login"), "{\"username\":\"alice\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access"));
    }

    @Test
    void apiDocsAndHealthArePublic() throws Exception {
        mockMvc.perform(api(get(CONTEXT_PATH + "/v3/api-docs"))).andExpect(status().isOk());
        mockMvc.perform(api(get(CONTEXT_PATH + "/actuator/health/liveness"))).andExpect(status().isOk());
    }

    @Test
    void protectedEndpointWithoutTokenReturnsJsonUnauthorized() throws Exception {
        mockMvc.perform(api(get(CONTEXT_PATH + "/v1/auth/me")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void accessTokenIsAccepted() throws Exception {
        mockMvc.perform(api(get(CONTEXT_PATH + "/v1/auth/me")).header("Authorization", "Bearer " + accessToken()))
                .andExpect(status().isOk());

        verify(authService).getUser(ALICE);
    }

    @Test
    void refreshTokenIsRejectedAsAccessToken() throws Exception {
        mockMvc.perform(api(get(CONTEXT_PATH + "/v1/auth/me")).header("Authorization", "Bearer " + tokenProvider.createRefreshToken(ALICE)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePasswordActsOnTheLoggedInUser() throws Exception {
        mockMvc.perform(changePassword("newPassword1").header("Authorization", "Bearer " + accessToken()))
                .andExpect(status().isOk());

        verify(authService).changePassword(eq(ALICE),
                eq(new ChangePasswordRequest("oldPassword1", "newPassword1", "newPassword1")));
    }

    @Test
    void mismatchedPasswordConfirmationIsAValidationError() throws Exception {
        mockMvc.perform(changePassword("somethingElse").header("Authorization", "Bearer " + accessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.confirmPasswordMatching").value("must match newPassword"));
    }

    @Test
    void registrationValidatesInput() throws Exception {
        mockMvc.perform(json(post(CONTEXT_PATH + "/v1/auth/register"), "{\"username\":\"a b\",\"email\":\"nope\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void malformedJsonIsABadRequestNotAServerError() throws Exception {
        mockMvc.perform(json(post(CONTEXT_PATH + "/v1/auth/login"), "{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"));
    }

    @Test
    void unknownEndpointReturnsJsonNotFound() throws Exception {
        mockMvc.perform(api(get(CONTEXT_PATH + "/v1/does-not-exist")).header("Authorization", "Bearer " + accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    void wrongHttpMethodReturnsMethodNotAllowed() throws Exception {
        mockMvc.perform(api(get(CONTEXT_PATH + "/v1/auth/change-password")).header("Authorization", "Bearer " + accessToken()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void everyResponseCarriesARequestId() throws Exception {
        mockMvc.perform(api(get(CONTEXT_PATH + "/v1/auth/me")).header(RequestIdFilter.HEADER, "lb-trace-123"))
                .andExpect(header().string(RequestIdFilter.HEADER, "lb-trace-123"))
                .andExpect(jsonPath("$.requestId").value("lb-trace-123"));

        // An unsafe incoming id is replaced, not echoed into logs and headers
        mockMvc.perform(api(get(CONTEXT_PATH + "/v1/auth/me")).header(RequestIdFilter.HEADER, "bad id\r\ninjected"))
                .andExpect(header().string(RequestIdFilter.HEADER, matchesPattern("[0-9a-f-]{36}")));
    }

    private String accessToken() {
        return tokenProvider.createAccessToken(ALICE, "alice", List.of("CUSTOMER"));
    }

    private MockHttpServletRequestBuilder changePassword(String confirmPassword) {
        return json(post(CONTEXT_PATH + "/v1/auth/change-password"),
                "{\"oldPassword\":\"oldPassword1\",\"newPassword\":\"newPassword1\",\"confirmPassword\":\"%s\"}"
                        .formatted(confirmPassword));
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return api(builder).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    /** Prefixes the context path the way a real request arrives. */
    private MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder builder) {
        return builder.contextPath(CONTEXT_PATH);
    }
}
