package com.groceryecom.platform.security;

import com.groceryecom.platform.ratelimit.RateLimitProperties;
import com.groceryecom.platform.security.token.TokenRegistry;
import com.groceryecom.shared.web.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/**
 * Stateless JWT security for the API.
 * Every endpoint requires authentication unless it is listed as public below.
 * Request matchers are relative to the /api context path.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class, RateLimitProperties.class})
public class SecurityConfig {

    private static final String[] PUBLIC_POST_ENDPOINTS = {
            "/v1/auth/register",
            "/v1/auth/login",
            "/v1/auth/refresh-token"
    };

    private static final String[] PUBLIC_GET_ENDPOINTS = {
            "/actuator/health", "/actuator/health/**", "/actuator/info",
            "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**",
            // A store's shop window, which a customer sees before signing in. One path
            // segment only, so /v1/vendors/{id}/anything stays authenticated; the service
            // returns approved stores and nothing else.
            "/v1/vendors/*",
            // The price list. Somebody deciding whether to sell here should not have to
            // create an account to see what it costs.
            "/v1/plans"
    };

    /**
     * Paths that a pattern in {@link #PUBLIC_GET_ENDPOINTS} would otherwise open up.
     * Matched first, so the more specific rule wins. Covered by {@code SecurityRulesTest}.
     */
    private static final String[] AUTHENTICATED_BEFORE_PUBLIC = {
            "/v1/vendors/me"
    };

    /**
     * Where a metrics scrape may come from. Metrics describe the system (endpoints, error
     * rates, pool sizes), so they are not public; but Prometheus cannot hold a user token,
     * so the rule is the network it runs on. In Docker and Kubernetes the scraper is on a
     * private address; a request from the internet is authenticated like any other.
     */
    private static final List<IpAddressMatcher> PRIVATE_NETWORKS = List.of(
            new IpAddressMatcher("127.0.0.1/32"),
            new IpAddressMatcher("::1/128"),
            new IpAddressMatcher("10.0.0.0/8"),
            new IpAddressMatcher("172.16.0.0/12"),
            new IpAddressMatcher("192.168.0.0/16"));

    private static final RequestMatcher METRICS_FROM_PRIVATE_NETWORK = request ->
            HttpMethod.GET.matches(request.getMethod())
                    && (request.getRequestURI().contains("/actuator/prometheus")
                    || request.getRequestURI().contains("/actuator/metrics"))
                    && isPrivateAddress(request.getRemoteAddr());

    /**
     * Stores hashes with an algorithm prefix ({bcrypt}...), so the algorithm can be
     * upgraded later without invalidating existing passwords.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtTokenProvider tokenProvider,
                                                   TokenRegistry tokenRegistry, CorsProperties corsProperties,
                                                   JsonMapper jsonMapper) {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                // Preflight (OPTIONS) is answered by this filter before authorization runs,
                // so a browser check never needs a token
                .cors(cors -> cors.configurationSource(corsConfigurationSource(corsProperties)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, ex) -> writeError(jsonMapper, response,
                                HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "Authentication required"))
                        .accessDeniedHandler((request, response, ex) -> writeError(jsonMapper, response,
                                HttpServletResponse.SC_FORBIDDEN, "ACCESS_DENIED", "Access denied")))
                .authorizeHttpRequests(authorize -> authorize
                        // Before the public list: /v1/vendors/* would otherwise match /me,
                        // which is the caller's own stores and must never be anonymous.
                        .requestMatchers(HttpMethod.GET, AUTHENTICATED_BEFORE_PUBLIC).authenticated()
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS).permitAll()
                        .requestMatchers(METRICS_FROM_PRIVATE_NETWORK).permitAll()
                        .requestMatchers("/error").permitAll()
                        // Includes a metrics scrape from anywhere else, which then needs a token
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(tokenProvider, tokenRegistry), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * One rule for every frontend of the platform: the customer app, the admin console
     * and each vendor storefront. Patterns let a new vendor subdomain work without a
     * redeploy; see {@link CorsProperties}.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(properties.allowedOriginPatterns());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Accept", "Accept-Language", "X-Request-Id", "Idempotency-Key"));
        // Headers a browser script is allowed to read from the response
        configuration.setExposedHeaders(List.of("X-Request-Id"));
        // Tokens travel in the Authorization header, not cookies
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(properties.maxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private static boolean isPrivateAddress(String remoteAddress) {
        return remoteAddress != null && PRIVATE_NETWORKS.stream().anyMatch(matcher -> matcher.matches(remoteAddress));
    }

    private static void writeError(JsonMapper jsonMapper, HttpServletResponse response, int status,
                                   String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), ApiResponse.error(code, message));
    }
}
