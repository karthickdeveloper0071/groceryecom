package com.groceryecom.platform.cache;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Redis caching. Boot auto-configures the cache manager and a StringRedisTemplate.
 * Add typed serializers per use case (for example, a template for a specific DTO);
 * don't use Jackson "default typing", which lets data in Redis choose which
 * classes get instantiated.
 */
@Configuration
@EnableCaching
public class CacheConfig {
}
