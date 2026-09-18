package com.groceryecom.platform.ratelimit;

import com.groceryecom.platform.ratelimit.RateLimitProperties.Policy;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What a client actually experiences when it hammers the login endpoint.
 */
class RateLimitFilterTest {

    private static final String CONTEXT_PATH = "/api";

    /** Two attempts allowed, so the third is the interesting one. */
    private static final RateLimitProperties LIMITS = new RateLimitProperties(
            true,
            new Policy(2, Duration.ofMinutes(1)),
            new Policy(2, Duration.ofMinutes(5)),
            new Policy(2, Duration.ofMinutes(10)),
            new Policy(2, Duration.ofMinutes(1)));

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AuthLikeController())
            .addFilters(new RateLimitFilter(new InMemoryRateLimiter(), LIMITS, JsonMapper.builder().build()))
            .build();

    @Test
    void blocksTheThirdLoginAttemptWithARetryAfterHeader() throws Exception {
        mockMvc.perform(login()).andExpect(status().isOk());
        mockMvc.perform(login()).andExpect(status().isOk());

        mockMvc.perform(login())
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void countsEachClientAddressSeparately() throws Exception {
        mockMvc.perform(login().with(remoteAddress("203.0.113.1"))).andExpect(status().isOk());
        mockMvc.perform(login().with(remoteAddress("203.0.113.1"))).andExpect(status().isOk());
        mockMvc.perform(login().with(remoteAddress("203.0.113.1"))).andExpect(status().isTooManyRequests());

        // A different caller is unaffected by the first one's attempts
        mockMvc.perform(login().with(remoteAddress("203.0.113.2"))).andExpect(status().isOk());
    }

    @Test
    void countsEachEndpointSeparately() throws Exception {
        mockMvc.perform(login()).andExpect(status().isOk());
        mockMvc.perform(login()).andExpect(status().isOk());
        mockMvc.perform(login()).andExpect(status().isTooManyRequests());

        mockMvc.perform(request(post(CONTEXT_PATH + "/v1/auth/register"))).andExpect(status().isOk());
    }

    @Test
    void doesNotLimitOtherEndpoints() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(request(get(CONTEXT_PATH + "/v1/auth/me"))).andExpect(status().isOk());
        }
    }

    @Test
    void canBeTurnedOffByConfiguration() throws Exception {
        RateLimitProperties disabled = new RateLimitProperties(false,
                LIMITS.login(), LIMITS.loginPerAccount(), LIMITS.register(), LIMITS.refreshToken());
        MockMvc unlimited = MockMvcBuilders.standaloneSetup(new AuthLikeController())
                .addFilters(new RateLimitFilter(new InMemoryRateLimiter(), disabled, JsonMapper.builder().build()))
                .build();

        for (int attempt = 0; attempt < 5; attempt++) {
            unlimited.perform(login()).andExpect(status().isOk());
        }
    }

    private MockHttpServletRequestBuilder login() {
        return request(post(CONTEXT_PATH + "/v1/auth/login"));
    }

    private MockHttpServletRequestBuilder request(MockHttpServletRequestBuilder builder) {
        return builder.contextPath(CONTEXT_PATH).contentType(MediaType.APPLICATION_JSON).content("{}");
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor remoteAddress(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    @RestController
    @RequestMapping("/v1/auth")
    static class AuthLikeController {

        @PostMapping("/login")
        String login() {
            return "ok";
        }

        @PostMapping("/register")
        String register() {
            return "ok";
        }

        @GetMapping("/me")
        String me() {
            return "ok";
        }
    }
}
