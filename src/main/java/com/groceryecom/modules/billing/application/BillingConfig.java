package com.groceryecom.modules.billing.application;

import com.groceryecom.modules.billing.infrastructure.RazorpayProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds {@code app.billing}: the licensing policy, and where Razorpay lives.
 *
 * <p>Note what is <em>not</em> bound here: the gateway keys. They are entered by an admin
 * and stored encrypted, so that installing them is a screen rather than a deployment.
 */
@Configuration
@EnableConfigurationProperties({BillingProperties.class, RazorpayProperties.class})
class BillingConfig {
}
