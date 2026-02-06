package com.fhir.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.Map;

/**
 * Audit log entry for FHIR resource operations.
 * Stored in MongoDB time series collections for efficient time-based queries.
 *
 * Collection naming: audit_{resourceType} (e.g., audit_patient, audit_observation)
 */
@Document
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    private String id;

    /**
     * Timestamp of the audit event (time series time field).
     */
    @Field("timestamp")
    private Instant timestamp;

    /**
     * Metadata for time series grouping.
     */
    @Field("metadata")
    private AuditMetadata metadata;

    /**
     * Type of operation performed.
     */
    @Field("action")
    private AuditAction action;

    /**
     * Resource ID that was affected.
     */
    @Field("resourceId")
    private String resourceId;

    /**
     * Resource type (e.g., Patient, Observation).
     */
    @Field("resourceType")
    private String resourceType;

    /**
     * Version of the resource after the operation.
     */
    @Field("versionId")
    private Long versionId;

    /**
     * User or system that performed the operation.
     */
    @Field("actor")
    private String actor;

    /**
     * Source IP address or system identifier.
     */
    @Field("source")
    private String source;

    /**
     * HTTP method used (GET, POST, PUT, DELETE).
     */
    @Field("httpMethod")
    private String httpMethod;

    /**
     * Request URI.
     */
    @Field("requestUri")
    private String requestUri;

    /**
     * HTTP status code of the response.
     */
    @Field("statusCode")
    private Integer statusCode;

    /**
     * Duration of the operation in milliseconds.
     */
    @Field("durationMs")
    private Long durationMs;

    /**
     * Size of the request body in bytes.
     */
    @Field("requestSize")
    private Long requestSize;

    /**
     * Size of the response body in bytes.
     */
    @Field("responseSize")
    private Long responseSize;

    /**
     * Error message if the operation failed.
     */
    @Field("errorMessage")
    private String errorMessage;

    /**
     * Additional context data.
     */
    @Field("context")
    private Map<String, Object> context;

    /**
     * Audit actions enum.
     */
    public enum AuditAction {
        CREATE,
        READ,
        UPDATE,
        DELETE,
        SEARCH,
        HISTORY,
        VREAD,
        TRANSACTION,
        BATCH,
        VALIDATE
    }

    /**
     * Metadata class for time series grouping.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditMetadata {

        @Field("resourceType")
        private String resourceType;

        @Field("actor")
        private String actor;

        @Field("source")
        private String source;
    }
}
