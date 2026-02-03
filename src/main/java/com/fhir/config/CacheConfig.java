package com.fhir.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
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
 * Cache configuration with support for both Redis (secured) and in-memory caching.
 *
 * Redis is used when spring.cache.type=redis (default in Docker)
 * In-memory cache is used when spring.cache.type=simple (default for local dev)
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

    /**
     * Redis cache manager - used when spring.cache.type=redis
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(defaultTtlMinutes))
                .disableCachingNullValues()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new GenericJackson2JsonRedisSerializer()));

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // Individual resource cache (Patient/123, Observation/456, etc.)
        cacheConfigurations.put("resources",
                defaultConfig.entryTtl(Duration.ofMinutes(resourceTtlMinutes)));

        // Search results cache (short TTL as data changes frequently)
        cacheConfigurations.put("searches",
                defaultConfig.entryTtl(Duration.ofMinutes(searchTtlMinutes)));

        // Metadata/CapabilityStatement cache (rarely changes)
        cacheConfigurations.put("metadata",
                defaultConfig.entryTtl(Duration.ofHours(metadataTtlHours)));

        // Resource counts cache
        cacheConfigurations.put("counts",
                defaultConfig.entryTtl(Duration.ofMinutes(10)));

        // ValueSet/CodeSystem cache (terminology rarely changes)
        cacheConfigurations.put("terminology",
                defaultConfig.entryTtl(Duration.ofHours(12)));

        // Validation results cache
        cacheConfigurations.put("validation",
                defaultConfig.entryTtl(Duration.ofMinutes(30)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }

    /**
     * Simple in-memory cache manager - used when spring.cache.type=simple (default)
     */
    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "simple", matchIfMissing = true)
    public CacheManager simpleCacheManager() {
        return new ConcurrentMapCacheManager(
                "resources",
                "searches",
                "metadata",
                "counts",
                "terminology",
                "validation"
        );
    }
}
