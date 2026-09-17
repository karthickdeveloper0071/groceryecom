package com.GroceryEcom.GorceryEcom.common.config;

import com.GroceryEcom.GorceryEcom.infrastructure.security.JwtTokenProvider;
import com.GroceryEcom.GorceryEcom.modules.user.dto.AuthTokenDTO;
import com.GroceryEcom.GorceryEcom.modules.user.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies URL and method security rules as seen through the /api context path.
 */
@SpringBootTest
@ActiveProfiles("test")
class SecurityRulesTest {

    private static final String CONTEXT_PATH = "/api";
    private static final String CHANGE_PASSWORD_BODY =
            "{\"oldPassword\":\"oldPassword1\",\"newPassword\":\"newPassword1\",\"confirmPassword\":\"newPassword1\"}";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void loginIsPublic() throws Exception {
        when(authService.login(any())).thenReturn(AuthTokenDTO.builder().accessToken("token").build());

        mockMvc.perform(api(post(CONTEXT_PATH + "/v1/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void verifyEmailIsPublic() throws Exception {
        mockMvc.perform(api(post(CONTEXT_PATH + "/v1/auth/verify-email")).param("token", "abc"))
                .andExpect(status().isOk());
    }

    @Test
    void apiDocsArePublic() throws Exception {
        mockMvc.perform(api(get(CONTEXT_PATH + "/v3/api-docs")))
                .andExpect(status().isOk());
    }

    @Test
    void unknownEndpointReturnsNotFound() throws Exception {
        String token = jwtTokenProvider.generateAccessToken(1L, "alice", "alice@example.com", List.of("CUSTOMER"));

        mockMvc.perform(api(get(CONTEXT_PATH + "/v1/does-not-exist")).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void protectedEndpointRequiresToken() throws Exception {
        mockMvc.perform(api(get(CONTEXT_PATH + "/v1/auth/me")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accessTokenIsAccepted() throws Exception {
        String token = jwtTokenProvider.generateAccessToken(1L, "alice", "alice@example.com", List.of("CUSTOMER"));

        mockMvc.perform(api(get(CONTEXT_PATH + "/v1/auth/me")).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void refreshTokenIsRejectedAsAccessToken() throws Exception {
        String token = jwtTokenProvider.generateRefreshToken(1L, "alice");

        mockMvc.perform(api(get(CONTEXT_PATH + "/v1/auth/me")).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userCanChangeOwnPassword() throws Exception {
        String token = jwtTokenProvider.generateAccessToken(1L, "alice", "alice@example.com", List.of("CUSTOMER"));

        mockMvc.perform(changePassword(1L, token)).andExpect(status().isOk());
    }

    @Test
    void userCannotChangeAnotherUsersPassword() throws Exception {
        String token = jwtTokenProvider.generateAccessToken(1L, "alice", "alice@example.com", List.of("CUSTOMER"));

        mockMvc.perform(changePassword(2L, token)).andExpect(status().isForbidden());
    }

    @Test
    void adminCanChangeAnotherUsersPassword() throws Exception {
        String token = jwtTokenProvider.generateAccessToken(9L, "admin", "admin@example.com", List.of("ADMIN"));

        mockMvc.perform(changePassword(2L, token)).andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder changePassword(Long userId, String token) {
        return api(post(CONTEXT_PATH + "/v1/auth/" + userId + "/change-password"))
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CHANGE_PASSWORD_BODY);
    }

    private MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder builder) {
        return builder.contextPath(CONTEXT_PATH);
    }
}
