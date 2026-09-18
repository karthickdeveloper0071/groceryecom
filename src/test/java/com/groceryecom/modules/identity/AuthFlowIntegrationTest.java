package com.groceryecom.modules.identity;

import com.groceryecom.PostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Registration, login, token refresh and password change end to end, against PostgreSQL.
 */
@SpringBootTest
class AuthFlowIntegrationTest extends PostgresIntegrationTest {

    private static final String CONTEXT_PATH = "/api";
    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

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
    void customerCanRegisterLogInRefreshAndChangePassword() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "Shopper" + suffix;
        String email = "Shopper" + suffix + "@Example.com";

        // Registration ignores any requested role and returns the public UUID, never the database id
        mockMvc.perform(json("/v1/auth/register",
                        "{\"username\":\"%s\",\"email\":\"%s\",\"password\":\"password123\",\"role\":\"ADMIN\"}"
                                .formatted(username, email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.user.id").value(matchesPattern(UUID_PATTERN)))
                .andExpect(jsonPath("$.data.user.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.data.user.email").value(email.toLowerCase()));

        assertThat(jdbc.queryForObject("SELECT role FROM users WHERE username = ?", String.class, username.toLowerCase()))
                .isEqualTo("CUSTOMER");
        // Password hashes carry their algorithm, so it can be upgraded later
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE username = ?", String.class, username.toLowerCase()))
                .startsWith("{bcrypt}");

        // Duplicate registration differing only by letter case is a conflict
        mockMvc.perform(json("/v1/auth/register",
                        "{\"username\":\"%s\",\"email\":\"other%s@example.com\",\"password\":\"password123\"}"
                                .formatted(username.toUpperCase(), suffix)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_EXISTS"));

        // Login by email in any letter case
        String loginBody = login(email.toUpperCase(), "password123");
        String accessToken = JsonPath.read(loginBody, "$.data.accessToken");
        String refreshToken = JsonPath.read(loginBody, "$.data.refreshToken");

        mockMvc.perform(api(get(CONTEXT_PATH + "/v1/auth/me")).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username.toLowerCase()));

        // A refresh token gets a fresh pair of tokens
        mockMvc.perform(json("/v1/auth/refresh-token", "{\"refreshToken\":\"%s\"}".formatted(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").value(startsWith("ey")));

        // An access token is not accepted as a refresh token
        mockMvc.perform(json("/v1/auth/refresh-token", "{\"refreshToken\":\"%s\"}".formatted(accessToken)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(json("/v1/auth/change-password",
                        "{\"oldPassword\":\"password123\",\"newPassword\":\"newPassword456\",\"confirmPassword\":\"newPassword456\"}")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        // Old password no longer works; new one does
        mockMvc.perform(json("/v1/auth/login", "{\"username\":\"%s\",\"password\":\"password123\"}".formatted(username)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        login(username, "newPassword456");
    }

    private String login(String identifier, String password) throws Exception {
        return mockMvc.perform(json("/v1/auth/login",
                        "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(identifier, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private MockHttpServletRequestBuilder json(String path, String body) {
        return api(post(CONTEXT_PATH + path)).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder builder) {
        return builder.contextPath(CONTEXT_PATH);
    }
}
