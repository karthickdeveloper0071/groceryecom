package com.groceryecom.platform.security;

import com.groceryecom.platform.security.token.TokenRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authenticates requests that carry a valid access token in the Authorization header.
 * Requests without one continue anonymously; the authorization rules then decide.
 * Created by {@link SecurityConfig} (not a {@code @Component}), so it only runs
 * inside the security filter chain and never a second time as a servlet filter.
 */
class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final TokenRegistry tokenRegistry;
    private final WebAuthenticationDetailsSource detailsSource = new WebAuthenticationDetailsSource();

    JwtAuthenticationFilter(JwtTokenProvider tokenProvider, TokenRegistry tokenRegistry) {
        this.tokenProvider = tokenProvider;
        this.tokenRegistry = tokenRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            tokenProvider.parseAccessToken(header.substring(BEARER_PREFIX.length()))
                    .filter(this::notRevoked)
                    .ifPresent(user -> authenticate(user, request));
        }
        filterChain.doFilter(request, response);
    }

    /** A signed token is not enough: logout, "log out everywhere" and a password change revoke it. */
    private boolean notRevoked(AuthenticatedUser user) {
        return !tokenRegistry.isRevoked(user.id(), user.tokenId(), user.sessionId(), user.issuedAt());
    }

    private void authenticate(AuthenticatedUser user, HttpServletRequest request) {
        var authorities = user.roles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        var authentication = UsernamePasswordAuthenticationToken.authenticated(user, null, authorities);
        authentication.setDetails(detailsSource.buildDetails(request));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }
}
