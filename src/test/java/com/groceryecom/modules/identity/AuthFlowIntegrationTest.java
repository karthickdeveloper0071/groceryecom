package com.groceryecom.modules.identity;

import com.groceryecom.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Registration, login and password change end to end, against PostgreSQL.
 */
@SpringBootTest
class AuthFlowIntegrationTest extends PostgresIntegrationTest {

    private static final String CONTEXT_PATH = "/api";
    private static final Pattern ACCESS_TOKEN = Pattern.compile("\"accessToken\":\"([^\"]+)\"");
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
    void customerCanRegisterLogInAndChangePassword() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "Shopper" + suffix;
        String email = "Shopper" + suffix + "@Example.com";

        // Registration ignores any requested role and returns the public UUID, never the database id
        mockMvc.perform(json(post(CONTEXT_PATH + "/v1/auth/register"),
                        "{\"username\":\"%s\",\"email\":\"%s\",\"password\":\"password123\",\"role\":\"ADMIN\"}"
                                .formatted(username, email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.user.id").value(matchesPattern(UUID_PATTERN)))
                .andExpect(jsonPath("$.data.user.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.data.user.email").value(email.toLowerCase()));

        assertThat(jdbc.queryForObject("SELECT role FROM users WHERE username = ?", String.class, username.toLowerCase()))
                .isEqualTo("CUSTOMER");

        // Duplicate registration differing only by case is rejected
        mockMvc.perform(json(post(CONTEXT_PATH + "/v1/auth/register"),
                        "{\"username\":\"%s\",\"email\":\"other%s@example.com\",\"password\":\"password123\"}"
                                .formatted(username.toUpperCase(), suffix)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("USERNAME_EXISTS"));

        // Login by email in any letter case
        String token = login(email.toUpperCase(), "password123");

        mockMvc.perform(api(get(CONTEXT_PATH + "/v1/auth/me")).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(username.toLowerCase()));

        mockMvc.perform(json(post(CONTEXT_PATH + "/v1/auth/change-password"),
                        "{\"oldPassword\":\"password123\",\"newPassword\":\"newPassword456\",\"confirmPassword\":\"newPassword456\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Old password no longer works; new one does
        mockMvc.perform(json(post(CONTEXT_PATH + "/v1/auth/login"),
                        "{\"username\":\"%s\",\"password\":\"password123\"}".formatted(username)))
                .andExpect(status().isUnauthorized());
        login(username, "newPassword456");
    }

    private String login(String identifier, String password) throws Exception {
        MvcResult result = mockMvc.perform(json(post(CONTEXT_PATH + "/v1/auth/login"),
                        "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(identifier, password)))
                .andExpect(status().isOk())
                .andReturn();
        Matcher matcher = ACCESS_TOKEN.matcher(result.getResponse().getContentAsString());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return api(builder).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder builder) {
        return builder.contextPath(CONTEXT_PATH);
    }
}
