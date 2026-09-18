package com.groceryecom.platform.security;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * Browser origins allowed to call the API, under {@code app.security.cors}.
 *
 * <p>This is a multi-vendor platform, so there is more than one frontend: the customer
 * app, the admin console, and a storefront per vendor. Patterns are used instead of an
 * exact list so that a new vendor subdomain works without a redeploy:
 * {@code https://*.groceryecom.com} matches {@code https://vendor-42.groceryecom.com}.
 *
 * <p>Each entry is a Spring {@code allowedOriginPattern}: scheme, host and port, where
 * {@code *} may stand for one host label or the port. A pattern must still be specific
 * about the scheme and the domain you own. Never use {@code *} alone in a shared
 * environment: it would let any website on the internet call the API with a user's token.
 *
 * <p>Vendors on their own domain (not a subdomain of ours) have to be listed explicitly
 * in {@code CORS_ALLOWED_ORIGINS}. When that list becomes unmanageable, replace this with
 * a {@code CorsConfigurationSource} that reads verified vendor domains from the vendor
 * module and caches them in Redis.
 *
 * @param allowedOriginPatterns origin patterns, comma-separated in the environment variable
 * @param maxAge how long a browser may cache the preflight answer
 */
@Validated
@ConfigurationProperties("app.security.cors")
public record CorsProperties(
        @NotEmpty List<String> allowedOriginPatterns,
        Duration maxAge) {

    public CorsProperties {
        maxAge = maxAge != null ? maxAge : Duration.ofHours(1);
    }
}
