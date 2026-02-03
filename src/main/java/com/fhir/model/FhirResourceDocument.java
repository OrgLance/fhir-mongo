package com.fhir.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.Document;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * MongoDB document for storing FHIR resources.
 * Optimized with compound indexes for billion-record scale.
 */
@org.springframework.data.mongodb.core.mapping.Document(collection = "fhir_resources")
@CompoundIndexes({
        // Primary lookup - unique resource identifier
        @CompoundIndex(name = "resource_lookup",
                       def = "{'resourceType': 1, 'resourceId': 1}",
                       unique = true),

        // CRITICAL: Search queries with deleted filter (most common query pattern)
        @CompoundIndex(name = "resource_type_deleted",
                       def = "{'resourceType': 1, 'deleted': 1}"),

        // Search with sorting by lastUpdated (covers 90% of search queries)
        @CompoundIndex(name = "resource_type_deleted_updated",
                       def = "{'resourceType': 1, 'deleted': 1, 'lastUpdated': -1}"),

        // Active resources query optimization
        @CompoundIndex(name = "resource_active_updated",
                       def = "{'resourceType': 1, 'active': 1, 'lastUpdated': -1}"),

        // Cursor-based pagination support (O(1) performance)
        @CompoundIndex(name = "cursor_pagination",
                       def = "{'resourceType': 1, 'deleted': 1, '_id': 1}"),

        // Patient reference lookup (common FHIR query)
        @CompoundIndex(name = "patient_reference",
                       def = "{'resourceType': 1, 'resourceData.subject.reference': 1, 'deleted': 1}",
                       sparse = true),

        // Code/coding lookup (Observation, Condition queries)
        @CompoundIndex(name = "coding_lookup",
                       def = "{'resourceType': 1, 'resourceData.code.coding.system': 1, 'resourceData.code.coding.code': 1, 'deleted': 1}",
                       sparse = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FhirResourceDocument {

    @Id
    private String id;

    @Indexed
    @Field("resourceType")
    private String resourceType;

    @Indexed
    @Field("resourceId")
    private String resourceId;

    // Full-text search support
    @TextIndexed(weight = 2)
    @Field("resourceJson")
    private String resourceJson;

    @Field("resourceData")
    private Document resourceData;

    @Field("versionId")
    private Long versionId;

    @Indexed
    @Field("lastUpdated")
    private Instant lastUpdated;

    @Field("createdAt")
    private Instant createdAt;

    // Index on active for filtering
    @Indexed
    @Field("active")
    private Boolean active;

    // CRITICAL: Index on deleted - used in almost every query
    @Indexed
    @Field("deleted")
    private Boolean deleted;

    // Compressed JSON for large resources (Bundles, DiagnosticReports)
    @Field("compressedJson")
    private byte[] compressedJson;

    // Flag to indicate if compression is used
    @Field("isCompressed")
    private Boolean isCompressed;
}
