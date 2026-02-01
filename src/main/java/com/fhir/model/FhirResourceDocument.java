package com.fhir.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.Document;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@org.springframework.data.mongodb.core.mapping.Document(collection = "fhir_resources")
@CompoundIndexes({
        @CompoundIndex(name = "resource_lookup", def = "{'resourceType': 1, 'resourceId': 1}", unique = true),
        @CompoundIndex(name = "resource_type_updated", def = "{'resourceType': 1, 'lastUpdated': -1}")
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

    @Field("resourceJson")
    private String resourceJson;

    @Field("resourceData")
    private Document resourceData;

    @Version
    @Field("versionId")
    private Long versionId;

    @Indexed
    @Field("lastUpdated")
    private Instant lastUpdated;

    @Field("createdAt")
    private Instant createdAt;

    @Field("active")
    private Boolean active;

    @Field("deleted")
    private Boolean deleted;
}
