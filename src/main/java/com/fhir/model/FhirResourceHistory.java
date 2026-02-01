package com.fhir.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Document(collection = "fhir_resource_history")
@CompoundIndexes({
        @CompoundIndex(name = "history_lookup", def = "{'resourceType': 1, 'resourceId': 1, 'versionId': -1}"),
        @CompoundIndex(name = "history_timestamp", def = "{'resourceType': 1, 'resourceId': 1, 'timestamp': -1}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FhirResourceHistory {

    @Id
    private String id;

    @Field("resourceType")
    private String resourceType;

    @Field("resourceId")
    private String resourceId;

    @Field("versionId")
    private Long versionId;

    @Field("resourceJson")
    private String resourceJson;

    @Field("timestamp")
    private Instant timestamp;

    @Field("action")
    private String action;
}
