package com.groceryecom.modules.identity;

import com.groceryecom.PostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sessions end to end: the promises a user relies on when they log out, sign out one
 * device, change their password, or lose a phone. A signed token still inside its
 * lifetime must stop working after each of these.
 */
@SpringBootTest
class SessionSecurityIntegrationTest extends PostgresIntegrationTest {

    private static final String CONTEXT_PATH = "/api";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void loggingOutStopsTheAccessTokenAndItsRefreshToken() throws Exception {
        Session session = register("Firefox on Linux");

        mockMvc.perform(me(session.accessToken())).andExpect(status().isOk());

        mockMvc.perform(post(CONTEXT_PATH + "/v1/auth/logout").contextPath(CONTEXT_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(me(session.accessToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        // The refresh token of a revoked session cannot buy a new one
        mockMvc.perform(json("/v1/auth/refresh-token",
                        "{\"refreshToken\":\"%s\"}".formatted(session.refreshToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loggingOutOfOneDeviceLeavesTheOtherLoggedIn() throws Exception {
        Session phone = register("GroceryEcom/1.0 (Android)");
        Session laptop = login(phone.username(), "Chrome on Windows");

        mockMvc.perform(post(CONTEXT_PATH + "/v1/auth/logout").contextPath(CONTEXT_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + laptop.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(me(laptop.accessToken())).andExpect(status().isUnauthorized());
        mockMvc.perform(me(phone.accessToken())).andExpect(status().isOk());
    }

    /** The device list, and signing out a device the user is not holding. */
    @Test
    void aUserSeesTheirDevicesAndCanSignOneOut() throws Exception {
        Session phone = register("GroceryEcom/1.0 (Android)");
        Session laptop = login(phone.username(), "Chrome on Windows");

        String body = mockMvc.perform(get(CONTEXT_PATH + "/v1/auth/sessions").contextPath(CONTEXT_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + laptop.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        List<String> userAgents = JsonPath.read(body, "$.data[*].userAgent");
        assertThat(userAgents).contains("GroceryEcom/1.0 (Android)", "Chrome on Windows");
        // Exactly one entry is the device making the request
        List<Boolean> current = JsonPath.read(body, "$.data[*].current");
        assertThat(current).containsExactlyInAnyOrder(true, false);

        String phoneSessionId = JsonPath.read(body, "$.data[?(@.userAgent == 'GroceryEcom/1.0 (Android)')].id")
                .toString().replaceAll("[\\[\\]\"]", "");

        mockMvc.perform(delete(CONTEXT_PATH + "/v1/auth/sessions/" + phoneSessionId).contextPath(CONTEXT_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + laptop.accessToken()))
                .andExpect(status().isOk());

        // The phone is out, the laptop that did it is still in
        mockMvc.perform(me(phone.accessToken())).andExpect(status().isUnauthorized());
        mockMvc.perform(me(laptop.accessToken())).andExpect(status().isOk());
        mockMvc.perform(get(CONTEXT_PATH + "/v1/auth/sessions").contextPath(CONTEXT_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + laptop.accessToken()))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void aUserCannotSignOutSomebodyElsesDevice() throws Exception {
        Session mine = register("Chrome on Windows");
        Session theirs = register("Safari on iPhone");

        String theirSessions = mockMvc.perform(get(CONTEXT_PATH + "/v1/auth/sessions").contextPath(CONTEXT_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + theirs.accessToken()))
                .andReturn().getResponse().getContentAsString();
        String theirSessionId = JsonPath.read(theirSessions, "$.data[0].id");

        mockMvc.perform(delete(CONTEXT_PATH + "/v1/auth/sessions/" + theirSessionId).contextPath(CONTEXT_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mine.accessToken()))
                .andExpect(status().isNotFound());

        mockMvc.perform(me(theirs.accessToken())).andExpect(status().isOk());
    }

    @Test
    void loggingOutEverywhereEndsEverySession() throws Exception {
        Session phone = register("GroceryEcom/1.0 (Android)");
        Session laptop = login(phone.username(), "Chrome on Windows");

        mockMvc.perform(post(CONTEXT_PATH + "/v1/auth/logout-all").contextPath(CONTEXT_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + laptop.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(me(phone.accessToken())).andExpect(status().isUnauthorized());
        mockMvc.perform(me(laptop.accessToken())).andExpect(status().isUnauthorized());

        // Logging in again works and is not caught by the cut-off
        mockMvc.perform(me(login(phone.username(), "Chrome on Windows").accessToken())).andExpect(status().isOk());
    }

    @Test
    void changingThePasswordEndsEverySession() throws Exception {
        Session phone = register("GroceryEcom/1.0 (Android)");
        Session laptop = login(phone.username(), "Chrome on Windows");

        mockMvc.perform(json("/v1/auth/change-password",
                        "{\"oldPassword\":\"password123\",\"newPassword\":\"newPassword456\","
                                + "\"confirmPassword\":\"newPassword456\"}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + laptop.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(me(phone.accessToken())).andExpect(status().isUnauthorized());
        mockMvc.perform(me(laptop.accessToken())).andExpect(status().isUnauthorized());
    }

    /** A refresh token works once; a second use means it leaked, so all sessions end. */
    @Test
    void reusingARefreshTokenEndsEverySession() throws Exception {
        Session phone = register("GroceryEcom/1.0 (Android)");
        Session laptop = login(phone.username(), "Chrome on Windows");

        String rotated = mockMvc.perform(json("/v1/auth/refresh-token",
                        "{\"refreshToken\":\"%s\"}".formatted(phone.refreshToken())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String newAccessToken = JsonPath.read(rotated, "$.data.accessToken");
        mockMvc.perform(me(newAccessToken)).andExpect(status().isOk());

        // Replaying the consumed refresh token
        mockMvc.perform(json("/v1/auth/refresh-token",
                        "{\"refreshToken\":\"%s\"}".formatted(phone.refreshToken())))
                .andExpect(status().isUnauthorized());

        // Every session of that user is gone, including the freshly rotated one
        mockMvc.perform(me(newAccessToken)).andExpect(status().isUnauthorized());
        mockMvc.perform(me(laptop.accessToken())).andExpect(status().isUnauthorized());
    }

    /** Rotation keeps one device as one session, rather than growing the list. */
    @Test
    void refreshingDoesNotCreateAnotherSession() throws Exception {
        Session phone = register("GroceryEcom/1.0 (Android)");

        String rotated = mockMvc.perform(json("/v1/auth/refresh-token",
                        "{\"refreshToken\":\"%s\"}".formatted(phone.refreshToken())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String newAccessToken = JsonPath.read(rotated, "$.data.accessToken");

        mockMvc.perform(get(CONTEXT_PATH + "/v1/auth/sessions").contextPath(CONTEXT_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + newAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].current").value(true));
    }

    private Session register(String userAgent) throws Exception {
        String username = "shopper" + UUID.randomUUID().toString().substring(0, 8);
        String body = mockMvc.perform(json("/v1/auth/register",
                        "{\"username\":\"%s\",\"email\":\"%s@example.com\",\"password\":\"password123\"}"
                                .formatted(username, username))
                        .header(HttpHeaders.USER_AGENT, userAgent))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return session(username, body);
    }

    private Session login(String username, String userAgent) throws Exception {
        String body = mockMvc.perform(json("/v1/auth/login",
                        "{\"username\":\"%s\",\"password\":\"password123\"}".formatted(username))
                        .header(HttpHeaders.USER_AGENT, userAgent))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return session(username, body);
    }

    private static Session session(String username, String responseBody) {
        return new Session(username,
                JsonPath.read(responseBody, "$.data.accessToken"),
                JsonPath.read(responseBody, "$.data.refreshToken"));
    }

    private MockHttpServletRequestBuilder me(String accessToken) {
        return get(CONTEXT_PATH + "/v1/auth/me").contextPath(CONTEXT_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
    }

    private MockHttpServletRequestBuilder json(String path, String body) {
        return post(CONTEXT_PATH + path).contextPath(CONTEXT_PATH)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private record Session(String username, String accessToken, String refreshToken) {
    }
}
