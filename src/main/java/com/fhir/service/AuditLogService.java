package com.fhir.service;

import com.fhir.model.AuditLog;
import com.fhir.model.AuditLog.AuditAction;
import com.fhir.model.AuditLog.AuditMetadata;
import com.fhir.model.AuditLog.FieldChange;
import com.fhir.model.AuditLog.FieldChange.ChangeType;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.TimeSeriesGranularity;
import com.mongodb.client.model.TimeSeriesOptions;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing FHIR resource audit logs using MongoDB time series collections.
 *
 * Features:
 * - Auto-creates time series collections per resource type
 * - Async logging for non-blocking operations
 * - Automatic data expiration (configurable retention)
 * - Optimized for time-based queries
 */
@Service
public class AuditLogService {

    private static final Logger logger = LoggerFactory.getLogger(AuditLogService.class);

    private static final String AUDIT_COLLECTION_PREFIX = "audit_";
    private static final String TIME_FIELD = "timestamp";
    private static final String META_FIELD = "metadata";
    private static final Set<String> IGNORED_FIELDS = Set.of("meta", "id");

    private final Gson gson = new Gson();

    @Autowired
    private MongoTemplate mongoTemplate;

    @Value("${fhir.audit.enabled:true}")
    private boolean auditEnabled;

    @Value("${fhir.audit.retention-days:90}")
    private int retentionDays;

    @Value("${fhir.audit.granularity:SECONDS}")
    private String granularity;

    // Track which collections have been initialized
    private final Set<String> initializedCollections = ConcurrentHashMap.newKeySet();

    @PostConstruct
    public void init() {
        if (auditEnabled) {
            logger.info("Audit logging enabled with {} days retention", retentionDays);
        } else {
            logger.info("Audit logging is disabled");
        }
    }

    /**
     * Get the audit collection name for a resource type.
     */
    public String getAuditCollectionName(String resourceType) {
        return AUDIT_COLLECTION_PREFIX + resourceType.toLowerCase();
    }

    /**
     * Ensure the time series collection exists for a resource type.
     * Creates it if it doesn't exist.
     */
    public void ensureAuditCollection(String resourceType) {
        String collectionName = getAuditCollectionName(resourceType);

        if (initializedCollections.contains(collectionName)) {
            return;
        }

        synchronized (this) {
            if (initializedCollections.contains(collectionName)) {
                return;
            }

            try {
                MongoDatabase database = mongoTemplate.getDb();

                // Check if collection already exists
                boolean exists = database.listCollectionNames()
                        .into(new java.util.ArrayList<>())
                        .contains(collectionName);

                if (!exists) {
                    createTimeSeriesCollection(database, collectionName);
                    logger.info("Created time series audit collection: {}", collectionName);
                } else {
                    logger.debug("Audit collection already exists: {}", collectionName);
                }

                initializedCollections.add(collectionName);
            } catch (Exception e) {
                logger.error("Failed to ensure audit collection {}: {}", collectionName, e.getMessage());
                // Still mark as initialized to avoid repeated failures
                initializedCollections.add(collectionName);
            }
        }
    }

    /**
     * Create a time series collection with proper configuration.
     */
    private void createTimeSeriesCollection(MongoDatabase database, String collectionName) {
        TimeSeriesGranularity tsGranularity = switch (granularity.toUpperCase()) {
            case "MINUTES" -> TimeSeriesGranularity.MINUTES;
            case "HOURS" -> TimeSeriesGranularity.HOURS;
            default -> TimeSeriesGranularity.SECONDS;
        };

        TimeSeriesOptions timeSeriesOptions = new TimeSeriesOptions(TIME_FIELD)
                .metaField(META_FIELD)
                .granularity(tsGranularity);

        CreateCollectionOptions options = new CreateCollectionOptions()
                .timeSeriesOptions(timeSeriesOptions);

        // Set expiration if retention is configured
        if (retentionDays > 0) {
            options.expireAfter(retentionDays * 24L * 60 * 60, java.util.concurrent.TimeUnit.SECONDS);
        }

        database.createCollection(collectionName, options);

        // Create additional indexes for common queries
        MongoCollection<Document> collection = database.getCollection(collectionName);
        collection.createIndex(new Document("resourceId", 1));
        collection.createIndex(new Document("action", 1));
        collection.createIndex(new Document("metadata.actor", 1));
    }

    /**
     * Log an audit event asynchronously.
     */
    @Async("auditTaskExecutor")
    public CompletableFuture<Void> logAsync(AuditLog auditLog) {
        if (!auditEnabled) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            log(auditLog);
        } catch (Exception e) {
            logger.error("Failed to log audit event: {}", e.getMessage());
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Log an audit event synchronously.
     */
    public void log(AuditLog auditLog) {
        if (!auditEnabled || auditLog == null) {
            return;
        }

        String resourceType = auditLog.getResourceType();
        if (resourceType == null || resourceType.isEmpty()) {
            logger.warn("Cannot log audit event without resource type");
            return;
        }

        // Ensure collection exists
        ensureAuditCollection(resourceType);

        // Set timestamp if not set
        if (auditLog.getTimestamp() == null) {
            auditLog.setTimestamp(Instant.now());
        }

        // Ensure metadata is set
        if (auditLog.getMetadata() == null) {
            auditLog.setMetadata(AuditMetadata.builder()
                    .resourceType(resourceType)
                    .actor(auditLog.getActor())
                    .source(auditLog.getSource())
                    .build());
        }

        String collectionName = getAuditCollectionName(resourceType);
        mongoTemplate.save(auditLog, collectionName);

        logger.debug("Audit logged: {} {} {}", auditLog.getAction(), resourceType, auditLog.getResourceId());
    }

    /**
     * Create an audit log for a resource operation.
     */
    public AuditLog createAuditLog(String resourceType, String resourceId, AuditAction action) {
        return AuditLog.builder()
                .timestamp(Instant.now())
                .resourceType(resourceType)
                .resourceId(resourceId)
                .action(action)
                .metadata(AuditMetadata.builder()
                        .resourceType(resourceType)
                        .build())
                .build();
    }

    /**
     * Create an audit log with full details.
     */
    public AuditLog createAuditLog(String resourceType, String resourceId, AuditAction action,
                                    Long versionId, String actor, String source,
                                    String httpMethod, String requestUri,
                                    Integer statusCode, Long durationMs) {
        return AuditLog.builder()
                .timestamp(Instant.now())
                .resourceType(resourceType)
                .resourceId(resourceId)
                .action(action)
                .versionId(versionId)
                .actor(actor)
                .source(source)
                .httpMethod(httpMethod)
                .requestUri(requestUri)
                .statusCode(statusCode)
                .durationMs(durationMs)
                .metadata(AuditMetadata.builder()
                        .resourceType(resourceType)
                        .actor(actor)
                        .source(source)
                        .build())
                .build();
    }

    /**
     * Log a CREATE operation with the new resource value.
     */
    @Async("auditTaskExecutor")
    public CompletableFuture<Void> logCreate(String resourceType, String resourceId, Long versionId,
                                              String actor, String source, Long durationMs,
                                              String newValue) {
        AuditLog log = createAuditLog(resourceType, resourceId, AuditAction.CREATE,
                versionId, actor, source, "POST", "/" + resourceType, 201, durationMs);
        log.setNewValue(newValue);
        return logAsync(log);
    }

    /**
     * Log a CREATE operation (without storing the value - for backward compatibility).
     */
    @Async("auditTaskExecutor")
    public CompletableFuture<Void> logCreate(String resourceType, String resourceId, Long versionId,
                                              String actor, String source, Long durationMs) {
        return logCreate(resourceType, resourceId, versionId, actor, source, durationMs, null);
    }

    /**
     * Log a READ operation.
     */
    @Async("auditTaskExecutor")
    public CompletableFuture<Void> logRead(String resourceType, String resourceId,
                                            String actor, String source, Long durationMs) {
        AuditLog log = createAuditLog(resourceType, resourceId, AuditAction.READ,
                null, actor, source, "GET", "/" + resourceType + "/" + resourceId, 200, durationMs);
        return logAsync(log);
    }

    /**
     * Log an UPDATE operation with old and new resource values.
     * Computes and stores the diff between old and new values.
     */
    @Async("auditTaskExecutor")
    public CompletableFuture<Void> logUpdate(String resourceType, String resourceId, Long versionId,
                                              String actor, String source, Long durationMs,
                                              String oldValue, String newValue) {
        AuditLog log = createAuditLog(resourceType, resourceId, AuditAction.UPDATE,
                versionId, actor, source, "PUT", "/" + resourceType + "/" + resourceId, 200, durationMs);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);

        // Compute changes between old and new values
        if (oldValue != null && newValue != null) {
            try {
                Map<String, FieldChange> changes = computeChanges(oldValue, newValue);
                log.setChanges(changes);
            } catch (Exception e) {
                logger.warn("Failed to compute changes for audit log: {}", e.getMessage());
            }
        }

        return logAsync(log);
    }

    /**
     * Log an UPDATE operation (without storing values - for backward compatibility).
     */
    @Async("auditTaskExecutor")
    public CompletableFuture<Void> logUpdate(String resourceType, String resourceId, Long versionId,
                                              String actor, String source, Long durationMs) {
        return logUpdate(resourceType, resourceId, versionId, actor, source, durationMs, null, null);
    }

    /**
     * Log a DELETE operation with the old resource value.
     */
    @Async("auditTaskExecutor")
    public CompletableFuture<Void> logDelete(String resourceType, String resourceId,
                                              String actor, String source, Long durationMs,
                                              String oldValue) {
        AuditLog log = createAuditLog(resourceType, resourceId, AuditAction.DELETE,
                null, actor, source, "DELETE", "/" + resourceType + "/" + resourceId, 204, durationMs);
        log.setOldValue(oldValue);
        return logAsync(log);
    }

    /**
     * Log a DELETE operation (without storing values - for backward compatibility).
     */
    @Async("auditTaskExecutor")
    public CompletableFuture<Void> logDelete(String resourceType, String resourceId,
                                              String actor, String source, Long durationMs) {
        return logDelete(resourceType, resourceId, actor, source, durationMs, null);
    }

    /**
     * Log a SEARCH operation.
     */
    @Async("auditTaskExecutor")
    public CompletableFuture<Void> logSearch(String resourceType, String actor, String source,
                                              String queryParams, int resultCount, Long durationMs) {
        AuditLog log = createAuditLog(resourceType, null, AuditAction.SEARCH,
                null, actor, source, "GET", "/" + resourceType + "?" + queryParams, 200, durationMs);
        log.setContext(Map.of("resultCount", resultCount, "queryParams", queryParams));
        return logAsync(log);
    }

    /**
     * Log an error.
     */
    @Async("auditTaskExecutor")
    public CompletableFuture<Void> logError(String resourceType, String resourceId, AuditAction action,
                                             String actor, String source, String errorMessage,
                                             Integer statusCode, Long durationMs) {
        AuditLog log = createAuditLog(resourceType, resourceId, action,
                null, actor, source, null, null, statusCode, durationMs);
        log.setErrorMessage(errorMessage);
        return logAsync(log);
    }

    /**
     * Query audit logs for a specific resource.
     */
    public java.util.List<AuditLog> getAuditLogsForResource(String resourceType, String resourceId,
                                                             Instant from, Instant to, int limit) {
        ensureAuditCollection(resourceType);
        String collectionName = getAuditCollectionName(resourceType);

        org.springframework.data.mongodb.core.query.Query query = new org.springframework.data.mongodb.core.query.Query();

        if (resourceId != null) {
            query.addCriteria(org.springframework.data.mongodb.core.query.Criteria.where("resourceId").is(resourceId));
        }

        if (from != null) {
            query.addCriteria(org.springframework.data.mongodb.core.query.Criteria.where("timestamp").gte(from));
        }

        if (to != null) {
            query.addCriteria(org.springframework.data.mongodb.core.query.Criteria.where("timestamp").lte(to));
        }

        query.with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "timestamp"));
        query.limit(limit > 0 ? limit : 100);

        return mongoTemplate.find(query, AuditLog.class, collectionName);
    }

    /**
     * Get audit logs for a resource in the last N hours.
     */
    public java.util.List<AuditLog> getRecentAuditLogs(String resourceType, String resourceId, int hours) {
        Instant from = Instant.now().minus(hours, ChronoUnit.HOURS);
        return getAuditLogsForResource(resourceType, resourceId, from, null, 100);
    }

    /**
     * Count audit logs by action type for a resource type.
     */
    public Map<AuditAction, Long> countByAction(String resourceType, Instant from, Instant to) {
        ensureAuditCollection(resourceType);
        String collectionName = getAuditCollectionName(resourceType);

        Map<AuditAction, Long> counts = new java.util.EnumMap<>(AuditAction.class);

        for (AuditAction action : AuditAction.values()) {
            org.springframework.data.mongodb.core.query.Query query = new org.springframework.data.mongodb.core.query.Query();
            query.addCriteria(org.springframework.data.mongodb.core.query.Criteria.where("action").is(action));

            if (from != null) {
                query.addCriteria(org.springframework.data.mongodb.core.query.Criteria.where("timestamp").gte(from));
            }
            if (to != null) {
                query.addCriteria(org.springframework.data.mongodb.core.query.Criteria.where("timestamp").lte(to));
            }

            long count = mongoTemplate.count(query, collectionName);
            if (count > 0) {
                counts.put(action, count);
            }
        }

        return counts;
    }

    /**
     * Compute field-level changes between old and new JSON values.
     *
     * @param oldJson The old JSON representation
     * @param newJson The new JSON representation
     * @return Map of field names to their changes
     */
    public Map<String, FieldChange> computeChanges(String oldJson, String newJson) {
        Map<String, FieldChange> changes = new HashMap<>();

        try {
            JsonObject oldObj = gson.fromJson(oldJson, JsonObject.class);
            JsonObject newObj = gson.fromJson(newJson, JsonObject.class);

            // Get all keys from both objects
            Set<String> allKeys = new HashSet<>();
            if (oldObj != null) {
                allKeys.addAll(oldObj.keySet());
            }
            if (newObj != null) {
                allKeys.addAll(newObj.keySet());
            }

            for (String key : allKeys) {
                // Skip meta and id fields as they always change
                if (IGNORED_FIELDS.contains(key)) {
                    continue;
                }

                JsonElement oldElement = oldObj != null ? oldObj.get(key) : null;
                JsonElement newElement = newObj != null ? newObj.get(key) : null;

                if (oldElement == null && newElement != null) {
                    // Field was added
                    changes.put(key, FieldChange.builder()
                            .field(key)
                            .oldValue(null)
                            .newValue(toSimpleValue(newElement))
                            .changeType(ChangeType.ADDED)
                            .build());
                } else if (oldElement != null && newElement == null) {
                    // Field was removed
                    changes.put(key, FieldChange.builder()
                            .field(key)
                            .oldValue(toSimpleValue(oldElement))
                            .newValue(null)
                            .changeType(ChangeType.REMOVED)
                            .build());
                } else if (oldElement != null && !oldElement.equals(newElement)) {
                    // Field was modified
                    changes.put(key, FieldChange.builder()
                            .field(key)
                            .oldValue(toSimpleValue(oldElement))
                            .newValue(toSimpleValue(newElement))
                            .changeType(ChangeType.MODIFIED)
                            .build());
                }
            }
        } catch (Exception e) {
            logger.error("Error computing changes: {}", e.getMessage());
        }

        return changes;
    }

    /**
     * Convert a JsonElement to a simple value for storage.
     * For complex objects, returns the JSON string representation.
     */
    private Object toSimpleValue(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            if (element.getAsJsonPrimitive().isBoolean()) {
                return element.getAsBoolean();
            } else if (element.getAsJsonPrimitive().isNumber()) {
                return element.getAsNumber();
            } else {
                return element.getAsString();
            }
        }
        // For arrays and objects, return the JSON string
        return element.toString();
    }
}
