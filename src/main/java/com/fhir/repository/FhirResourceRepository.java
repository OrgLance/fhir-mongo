package com.fhir.repository;

import com.fhir.model.FhirResourceDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface FhirResourceRepository extends MongoRepository<FhirResourceDocument, String> {

    Optional<FhirResourceDocument> findByResourceTypeAndResourceId(String resourceType, String resourceId);

    Optional<FhirResourceDocument> findByResourceTypeAndResourceIdAndDeletedFalse(String resourceType, String resourceId);

    Page<FhirResourceDocument> findByResourceTypeAndDeletedFalse(String resourceType, Pageable pageable);

    List<FhirResourceDocument> findByResourceTypeAndDeletedFalse(String resourceType);

    Page<FhirResourceDocument> findByDeletedFalse(Pageable pageable);

    @Query("{'resourceType': ?0, 'lastUpdated': {'$gte': ?1}, 'deleted': false}")
    List<FhirResourceDocument> findByResourceTypeAndLastUpdatedAfter(String resourceType, Instant since);

    @Query("{'resourceType': ?0, 'lastUpdated': {'$gte': ?1, '$lte': ?2}, 'deleted': false}")
    List<FhirResourceDocument> findByResourceTypeAndLastUpdatedBetween(String resourceType, Instant start, Instant end);

    long countByResourceTypeAndDeletedFalse(String resourceType);

    boolean existsByResourceTypeAndResourceId(String resourceType, String resourceId);

    @Query("{'resourceType': ?0, 'resourceData.?1': ?2, 'deleted': false}")
    List<FhirResourceDocument> findByResourceTypeAndField(String resourceType, String fieldPath, Object value);

    void deleteByResourceTypeAndResourceId(String resourceType, String resourceId);
}
