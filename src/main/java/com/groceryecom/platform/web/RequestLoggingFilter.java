package com.groceryecom.platform.web;

import com.groceryecom.platform.security.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * One log line per HTTP request, so a problem report can be traced to an endpoint
 * without reproducing it:
 *
 * <pre>
 * POST /api/v1/auth/login -> 401 in 87ms user=anonymous ip=203.0.113.7
 * POST /api/v1/auth/register -> 409 in 42ms user=anonymous ip=203.0.113.7 body={"username":"alice","password":"***"}
 * </pre>
 *
 * <p>The level tells you who should care: INFO for a normal request, WARN when the
 * caller was wrong (4xx), ERROR when we were wrong (5xx).
 *
 * <p>The request body is logged only when the request failed, only for JSON, only up to
 * {@value #MAX_BODY_CHARS} characters, and only after {@link SensitiveData} has masked
 * secrets. Successful requests do not need their body in the log, and logging every body
 * would fill the disk and copy personal data into log storage.
 *
 * <p>Runs after {@link RequestIdFilter}, so every line carries the trace id.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter extends OncePerRequestFilter {

    static final int MAX_BODY_CHARS = 2048;
    private static final int MAX_CACHED_BODY_BYTES = 8192;
    private static final String ANONYMOUS = "anonymous";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Health probes and metric scrapes would drown out real traffic
        return request.getRequestURI().contains("/actuator/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        HttpServletRequest toUse = shouldCacheBody(request)
                ? new ContentCachingRequestWrapper(request, MAX_CACHED_BODY_BYTES)
                : request;

        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(toUse, response);
        } finally {
            long millis = (System.nanoTime() - startedAt) / 1_000_000;
            logRequest(toUse, response.getStatus(), millis);
        }
    }

    private void logRequest(HttpServletRequest request, int status, long millis) {
        String line = "{} {} -> {} in {}ms user={} ip={}{}";
        Object[] arguments = {
                request.getMethod(),
                pathWithQuery(request),
                status,
                millis,
                currentUser(),
                clientIp(request),
                status >= 400 ? " body=" + bodyOf(request) : ""
        };

        if (status >= 500) {
            log.error(line, arguments);
        } else if (status >= 400) {
            log.warn(line, arguments);
        } else {
            log.info(line, arguments);
        }
    }

    private static boolean shouldCacheBody(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase().startsWith(MediaType.APPLICATION_JSON_VALUE);
    }

    private static String bodyOf(HttpServletRequest request) {
        if (!(request instanceof ContentCachingRequestWrapper wrapper)) {
            return "<not captured>";
        }
        byte[] content = wrapper.getContentAsByteArray();
        if (content.length == 0) {
            return "<empty>";
        }
        return SensitiveData.maskAndTruncate(new String(content, StandardCharsets.UTF_8), MAX_BODY_CHARS);
    }

    /** The query string can carry identifiers worth seeing, so it is masked rather than dropped. */
    private static String pathWithQuery(HttpServletRequest request) {
        String query = request.getQueryString();
        return query == null ? request.getRequestURI()
                : request.getRequestURI() + "?" + SensitiveData.maskAndTruncate(query, 256);
    }

    private static String currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.id().toString();
        }
        return ANONYMOUS;
    }

    private static String clientIp(HttpServletRequest request) {
        // forward-headers-strategy=framework already resolved X-Forwarded-For
        String remote = request.getRemoteAddr();
        return remote != null ? remote : "unknown";
    }
}
