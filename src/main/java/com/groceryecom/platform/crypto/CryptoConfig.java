package com.groceryecom.platform.crypto;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Binds {@code app.security.secrets}. */
@Configuration
@EnableConfigurationProperties(CryptoProperties.class)
public class CryptoConfig {
}
