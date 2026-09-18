package com.groceryecom.platform.ratelimit;

import com.groceryecom.platform.ratelimit.RateLimitProperties.Policy;
import com.groceryecom.shared.web.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Map;

/**
 * Applies the per-address limits to the public authentication endpoints.
 *
 * <p>It runs before Spring Security, so a flood of guesses is stopped before any password
 * hashing happens: BCrypt is deliberately slow, which makes unlimited login attempts a way
 * to exhaust the server as well as to guess passwords.
 *
 * <p>Answers 429 with the standard error envelope and a {@code Retry-After} header. The
 * body is written here because a filter's exception never reaches
 * {@code GlobalExceptionHandler}.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;
    private final RateLimitProperties properties;
    private final JsonMapper jsonMapper;
    private final Map<String, Policy> policiesByPath;

    RateLimitFilter(RateLimiter rateLimiter, RateLimitProperties properties, JsonMapper jsonMapper) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.policiesByPath = Map.of(
                "/v1/auth/login", properties.login(),
                "/v1/auth/register", properties.register(),
                "/v1/auth/refresh-token", properties.refreshToken());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.enabled() || !HttpMethod.POST.matches(request.getMethod()) || policyFor(request) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Policy policy = policyFor(request);
        String path = pathWithinApplication(request);
        // Trustworthy because X-Forwarded-For is only honoured from configured proxies
        String key = path + ":ip:" + request.getRemoteAddr();

        RateLimiter.Decision decision = rateLimiter.check(key, policy);
        if (!decision.allowed()) {
            log.info("Rate limit hit for {} from {}", path, request.getRemoteAddr());
            writeTooManyRequests(response, decision.retryAfterSeconds());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private Policy policyFor(HttpServletRequest request) {
        return policiesByPath.get(pathWithinApplication(request));
    }

    /** Strips the /api context path, so the keys match the paths used elsewhere. */
    private static String pathWithinApplication(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)
                ? uri.substring(contextPath.length())
                : uri;
    }

    private void writeTooManyRequests(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        jsonMapper.writeValue(response.getOutputStream(), ApiResponse.error("RATE_LIMITED",
                "Too many requests. Try again in " + retryAfterSeconds + " seconds."));
    }
}
