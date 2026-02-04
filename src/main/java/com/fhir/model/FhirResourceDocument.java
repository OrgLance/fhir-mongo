package com.fhir.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.Document;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * MongoDB document for storing FHIR resources.
 *
 * Each resource type is stored in its own collection (e.g., "patient", "observation").
 * Collection name is determined dynamically by DynamicFhirResourceRepository.
 * Indexes are created per collection for optimal performance.
 */
@org.springframework.data.mongodb.core.mapping.Document
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FhirResourceDocument {

    @Id
    private String id;

    @Field("resourceType")
    private String resourceType;

    @Field("resourceId")
    private String resourceId;

    @Field("resourceJson")
    private String resourceJson;

    @Field("resourceData")
    private Document resourceData;

    @Field("versionId")
    private Long versionId;

    @Field("lastUpdated")
    private Instant lastUpdated;

    @Field("createdAt")
    private Instant createdAt;

    @Field("active")
    private Boolean active;

    @Field("deleted")
    private Boolean deleted;

    // Compressed JSON for large resources (Bundles, DiagnosticReports)
    @Field("compressedJson")
    private byte[] compressedJson;

    // Flag to indicate if compression is used
    @Field("isCompressed")
    private Boolean isCompressed;
}
