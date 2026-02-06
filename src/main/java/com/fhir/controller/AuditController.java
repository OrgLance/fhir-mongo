package com.fhir.controller;

import com.fhir.model.AuditLog;
import com.fhir.model.AuditLog.AuditAction;
import com.fhir.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for audit log operations.
 * Provides endpoints to query audit logs stored in MongoDB time series collections.
 */
@RestController
@RequestMapping("/fhir/_audit")
@Tag(name = "Audit", description = "Audit log operations for FHIR resources")
public class AuditController {

    @Autowired
    private AuditLogService auditLogService;

    /**
     * Get audit log status and configuration.
     */
    @GetMapping("/status")
    @Operation(summary = "Get audit system status", description = "Returns audit logging configuration and time series collection status")
    public ResponseEntity<Map<String, Object>> getAuditStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", true);
        status.put("storageType", "MongoDB Time Series Collections");
        status.put("collections", auditLogService.getAuditCollections());
        return ResponseEntity.ok(status);
    }

    /**
     * Get collection info for a specific resource type.
     */
    @GetMapping("/collections/{resourceType}")
    @Operation(summary = "Get audit collection info", description = "Returns time series collection configuration for a resource type")
    public ResponseEntity<Map<String, Object>> getCollectionInfo(
            @Parameter(description = "FHIR resource type (e.g., Patient, Observation)")
            @PathVariable String resourceType) {
        Map<String, Object> info = auditLogService.getCollectionInfo(resourceType);
        return ResponseEntity.ok(info);
    }

    /**
     * Get audit logs for a specific resource.
     */
    @GetMapping("/{resourceType}/{resourceId}")
    @Operation(summary = "Get audit logs for a resource", description = "Returns audit trail for a specific FHIR resource")
    public ResponseEntity<List<AuditLog>> getResourceAuditLogs(
            @Parameter(description = "FHIR resource type")
            @PathVariable String resourceType,
            @Parameter(description = "Resource ID")
            @PathVariable String resourceId,
            @Parameter(description = "Start time (ISO format)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(description = "End time (ISO format)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @Parameter(description = "Maximum number of results")
            @RequestParam(defaultValue = "100") int limit) {

        Instant fromInstant = from != null ? from.toInstant(ZoneOffset.UTC) : null;
        Instant toInstant = to != null ? to.toInstant(ZoneOffset.UTC) : null;

        List<AuditLog> logs = auditLogService.getAuditLogsForResource(
                resourceType, resourceId, fromInstant, toInstant, limit);
        return ResponseEntity.ok(logs);
    }

    /**
     * Get audit logs for a resource type.
     */
    @GetMapping("/{resourceType}")
    @Operation(summary = "Get audit logs for a resource type", description = "Returns audit logs for all resources of a type")
    public ResponseEntity<List<AuditLog>> getTypeAuditLogs(
            @Parameter(description = "FHIR resource type")
            @PathVariable String resourceType,
            @Parameter(description = "Start time (ISO format)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(description = "End time (ISO format)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @Parameter(description = "Maximum number of results")
            @RequestParam(defaultValue = "100") int limit) {

        Instant fromInstant = from != null ? from.toInstant(ZoneOffset.UTC) : null;
        Instant toInstant = to != null ? to.toInstant(ZoneOffset.UTC) : null;

        List<AuditLog> logs = auditLogService.getAuditLogsForResource(
                resourceType, null, fromInstant, toInstant, limit);
        return ResponseEntity.ok(logs);
    }

    /**
     * Get recent audit logs for a resource.
     */
    @GetMapping("/{resourceType}/{resourceId}/recent")
    @Operation(summary = "Get recent audit logs", description = "Returns audit logs from the last N hours")
    public ResponseEntity<List<AuditLog>> getRecentAuditLogs(
            @Parameter(description = "FHIR resource type")
            @PathVariable String resourceType,
            @Parameter(description = "Resource ID")
            @PathVariable String resourceId,
            @Parameter(description = "Number of hours to look back")
            @RequestParam(defaultValue = "24") int hours) {

        List<AuditLog> logs = auditLogService.getRecentAuditLogs(resourceType, resourceId, hours);
        return ResponseEntity.ok(logs);
    }

    /**
     * Get audit statistics by action type.
     */
    @GetMapping("/{resourceType}/stats")
    @Operation(summary = "Get audit statistics", description = "Returns count of audit events by action type")
    public ResponseEntity<Map<AuditAction, Long>> getAuditStats(
            @Parameter(description = "FHIR resource type")
            @PathVariable String resourceType,
            @Parameter(description = "Start time (ISO format)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(description = "End time (ISO format)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        Instant fromInstant = from != null ? from.toInstant(ZoneOffset.UTC) : null;
        Instant toInstant = to != null ? to.toInstant(ZoneOffset.UTC) : null;

        Map<AuditAction, Long> stats = auditLogService.countByAction(resourceType, fromInstant, toInstant);
        return ResponseEntity.ok(stats);
    }
}
