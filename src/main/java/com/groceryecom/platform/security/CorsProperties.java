package com.groceryecom.platform.security;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Browser origins allowed to call the API, under {@code app.security.cors}.
 * Set CORS_ALLOWED_ORIGINS to a comma-separated list per environment.
 */
@Validated
@ConfigurationProperties("app.security.cors")
public record CorsProperties(@NotEmpty List<String> allowedOrigins) {
}
