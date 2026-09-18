package com.groceryecom.modules.billing.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Binds {@code app.billing} so the licensing policy is configuration, not code. */
@Configuration
@EnableConfigurationProperties(BillingProperties.class)
class BillingConfig {
}
