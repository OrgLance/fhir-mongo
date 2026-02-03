package com.fhir.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis caching configuration for high-performance FHIR resource access.
 * Reduces database load by 80%+ for read-heavy workloads.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Value("${fhir.cache.default-ttl-minutes:60}")
    private long defaultTtlMinutes;

    @Value("${fhir.cache.resource-ttl-minutes:30}")
    private long resourceTtlMinutes;

    @Value("${fhir.cache.search-ttl-minutes:5}")
    private long searchTtlMinutes;

    @Value("${fhir.cache.metadata-ttl-hours:24}")
    private long metadataTtlHours;

    @Bean
    public RedisCacheConfiguration defaultCacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(defaultTtlMinutes))
                .disableCachingNullValues()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new GenericJackson2JsonRedisSerializer()));
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // Individual resource cache (Patient/123, Observation/456, etc.)
        cacheConfigurations.put("resources",
                defaultCacheConfiguration().entryTtl(Duration.ofMinutes(resourceTtlMinutes)));

        // Search results cache (short TTL as data changes frequently)
        cacheConfigurations.put("searches",
                defaultCacheConfiguration().entryTtl(Duration.ofMinutes(searchTtlMinutes)));

        // Metadata/CapabilityStatement cache (rarely changes)
        cacheConfigurations.put("metadata",
                defaultCacheConfiguration().entryTtl(Duration.ofHours(metadataTtlHours)));

        // Resource counts cache
        cacheConfigurations.put("counts",
                defaultCacheConfiguration().entryTtl(Duration.ofMinutes(10)));

        // ValueSet/CodeSystem cache (terminology rarely changes)
        cacheConfigurations.put("terminology",
                defaultCacheConfiguration().entryTtl(Duration.ofHours(12)));

        // Validation results cache
        cacheConfigurations.put("validation",
                defaultCacheConfiguration().entryTtl(Duration.ofMinutes(30)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultCacheConfiguration())
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }
}
