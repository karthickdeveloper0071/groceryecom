package com.groceryecom.modules.billing.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where Razorpay lives, under {@code app.billing.razorpay}.
 *
 * <p>Only the address is configuration. The keys are not here on purpose: they are
 * entered by an admin and stored encrypted, so that installing them is a screen rather
 * than a deploy ({@code GatewayCredentialsService}).
 *
 * <p>The URL is configurable so a test can point the adapter at a stub instead of the
 * internet, and never so that a live system talks to something else by accident.
 */
@ConfigurationProperties("app.billing.razorpay")
public record RazorpayProperties(String apiUrl) {

    public RazorpayProperties {
        apiUrl = apiUrl != null ? apiUrl : "https://api.razorpay.com";
    }
}
