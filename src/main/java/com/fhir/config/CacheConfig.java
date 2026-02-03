package com.fhir.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Simple in-memory caching configuration for FHIR resource access.
 * Uses ConcurrentHashMap-based cache (no external dependencies).
 *
 * For production with high traffic, consider enabling Redis caching.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        // Simple in-memory cache with predefined cache names
        return new ConcurrentMapCacheManager(
                "resources",      // Individual resource cache (Patient/123, etc.)
                "searches",       // Search results cache
                "metadata",       // Metadata/CapabilityStatement cache
                "counts",         // Resource counts cache
                "terminology",    // ValueSet/CodeSystem cache
                "validation"      // Validation results cache
        );
    }
}
