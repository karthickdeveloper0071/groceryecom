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
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Replay detection must be written to the database, not only to the Redis cache.
 * Otherwise a Redis restart brings the attacker's sessions back to life.
 */
@SpringBootTest
class ReplayRevocationPersistenceTest extends PostgresIntegrationTest {

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
    void replayMarksEverySessionOfThatUserRevokedInTheDatabase() throws Exception {
        String username = "shopper" + UUID.randomUUID().toString().substring(0, 8);
        String registration = mockMvc.perform(json("/v1/auth/register",
                        "{\"username\":\"%s\",\"email\":\"%s@example.com\",\"password\":\"password123\"}"
                                .formatted(username, username)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String refreshToken = JsonPath.read(registration, "$.data.refreshToken");

        // Use it once (valid), then replay it
        mockMvc.perform(json("/v1/auth/refresh-token", "{\"refreshToken\":\"%s\"}".formatted(refreshToken)))
                .andExpect(status().isOk());
        mockMvc.perform(json("/v1/auth/refresh-token", "{\"refreshToken\":\"%s\"}".formatted(refreshToken)))
                .andExpect(status().isUnauthorized());

        Integer liveSessions = jdbc.queryForObject("""
                SELECT count(*) FROM user_sessions s
                  JOIN users u ON u.id = s.user_id
                 WHERE u.username = ? AND s.revoked_at IS NULL
                """, Integer.class, username);

        assertThat(liveSessions)
                .as("every session must be revoked in the database after a refresh-token replay")
                .isZero();
    }

    private MockHttpServletRequestBuilder json(String path, String body) {
        return post(CONTEXT_PATH + path).contextPath(CONTEXT_PATH)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
