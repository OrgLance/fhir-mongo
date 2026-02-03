package com.fhir.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.connection.ConnectionPoolSettings;
import com.mongodb.connection.SocketSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import java.util.concurrent.TimeUnit;

/**
 * MongoDB configuration optimized for high-throughput FHIR operations.
 * Supports connection pooling for concurrent access at scale.
 */
@Configuration
public class MongoConfig {

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    @Value("${fhir.mongodb.pool.max-size:200}")
    private int maxPoolSize;

    @Value("${fhir.mongodb.pool.min-size:20}")
    private int minPoolSize;

    @Value("${fhir.mongodb.pool.max-wait-time-ms:5000}")
    private int maxWaitTimeMs;

    @Value("${fhir.mongodb.pool.max-connection-idle-time-ms:60000}")
    private int maxConnectionIdleTimeMs;

    @Value("${fhir.mongodb.pool.max-connection-life-time-minutes:30}")
    private int maxConnectionLifeTimeMinutes;

    @Value("${fhir.mongodb.socket.connect-timeout-ms:10000}")
    private int connectTimeoutMs;

    @Value("${fhir.mongodb.socket.read-timeout-ms:30000}")
    private int readTimeoutMs;

    @Bean
    public MongoClientSettings mongoClientSettings() {
        return MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(mongoUri))
                // Connection pool settings for high concurrency
                .applyToConnectionPoolSettings(builder -> builder
                        .maxSize(maxPoolSize)
                        .minSize(minPoolSize)
                        .maxWaitTime(maxWaitTimeMs, TimeUnit.MILLISECONDS)
                        .maxConnectionIdleTime(maxConnectionIdleTimeMs, TimeUnit.MILLISECONDS)
                        .maxConnectionLifeTime(maxConnectionLifeTimeMinutes, TimeUnit.MINUTES)
                        .maintenanceInitialDelay(0, TimeUnit.MILLISECONDS)
                        .maintenanceFrequency(60, TimeUnit.SECONDS))
                // Socket settings
                .applyToSocketSettings(builder -> builder
                        .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
                        .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS))
                // Server settings
                .applyToServerSettings(builder -> builder
                        .heartbeatFrequency(10, TimeUnit.SECONDS)
                        .minHeartbeatFrequency(500, TimeUnit.MILLISECONDS))
                // Write concern for durability vs performance balance
                .writeConcern(WriteConcern.MAJORITY.withWTimeout(5000, TimeUnit.MILLISECONDS))
                // Read preference - prefer secondaries for read scaling
                .readPreference(ReadPreference.secondaryPreferred())
                // Retry settings
                .retryWrites(true)
                .retryReads(true)
                .build();
    }

    @Bean
    public MongoTemplate mongoTemplate(MongoDatabaseFactory mongoDbFactory, MongoMappingContext context) {
        MappingMongoConverter converter = new MappingMongoConverter(
                new DefaultDbRefResolver(mongoDbFactory), context);
        // Remove _class field to save storage space
        converter.setTypeMapper(new DefaultMongoTypeMapper(null));
        return new MongoTemplate(mongoDbFactory, converter);
    }
}
