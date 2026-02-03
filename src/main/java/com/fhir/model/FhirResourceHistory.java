package com.fhir.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * MongoDB document for storing FHIR resource version history.
 * Includes TTL index for automatic cleanup of old history records.
 */
@Document(collection = "fhir_resource_history")
@CompoundIndexes({
        // Primary history lookup
        @CompoundIndex(name = "history_lookup",
                       def = "{'resourceType': 1, 'resourceId': 1, 'versionId': -1}"),

        // Timestamp-based queries
        @CompoundIndex(name = "history_timestamp",
                       def = "{'resourceType': 1, 'resourceId': 1, 'timestamp': -1}"),

        // Type-level history queries
        @CompoundIndex(name = "type_history",
                       def = "{'resourceType': 1, 'timestamp': -1}"),

        // Action-based queries (for audit)
        @CompoundIndex(name = "action_lookup",
                       def = "{'resourceType': 1, 'action': 1, 'timestamp': -1}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FhirResourceHistory {

    @Id
    private String id;

    @Indexed
    @Field("resourceType")
    private String resourceType;

    @Indexed
    @Field("resourceId")
    private String resourceId;

    @Field("versionId")
    private Long versionId;

    @Field("resourceJson")
    private String resourceJson;

    // TTL index: Auto-delete history records after 2 years (configurable)
    // To enable: db.fhir_resource_history.createIndex({"timestamp": 1}, {expireAfterSeconds: 63072000})
    @Indexed
    @Field("timestamp")
    private Instant timestamp;

    @Indexed
    @Field("action")
    private String action;

    // Compressed JSON for storage efficiency
    @Field("compressedJson")
    private byte[] compressedJson;

    // Flag to indicate if compression is used
    @Field("isCompressed")
    private Boolean isCompressed;
}
