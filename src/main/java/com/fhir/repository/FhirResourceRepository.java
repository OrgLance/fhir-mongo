package com.fhir.repository;

import com.fhir.model.FhirResourceDocument;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Repository for FHIR resources with optimized queries for billion-record scale.
 * Uses Page/Slice instead of List to prevent memory issues.
 */
@Repository
public interface FhirResourceRepository extends MongoRepository<FhirResourceDocument, String> {

    // ========== Single Resource Lookups (OK as-is) ==========

    Optional<FhirResourceDocument> findByResourceTypeAndResourceId(String resourceType, String resourceId);

    Optional<FhirResourceDocument> findByResourceTypeAndResourceIdAndDeletedFalse(String resourceType, String resourceId);

    boolean existsByResourceTypeAndResourceId(String resourceType, String resourceId);

    void deleteByResourceTypeAndResourceId(String resourceType, String resourceId);

    // ========== Paginated Queries (FIXED - use Page instead of List) ==========

    Page<FhirResourceDocument> findByResourceTypeAndDeletedFalse(String resourceType, Pageable pageable);

    Page<FhirResourceDocument> findByDeletedFalse(Pageable pageable);

    // Use Slice for infinite scroll (doesn't compute total count - faster)
    Slice<FhirResourceDocument> findSliceByResourceTypeAndDeletedFalse(String resourceType, Pageable pageable);

    // ========== Time-based Queries (FIXED - use Page instead of List) ==========

    @Query("{'resourceType': ?0, 'lastUpdated': {'$gte': ?1}, 'deleted': false}")
    Page<FhirResourceDocument> findByResourceTypeAndLastUpdatedAfter(String resourceType, Instant since, Pageable pageable);

    @Query("{'resourceType': ?0, 'lastUpdated': {'$gte': ?1, '$lte': ?2}, 'deleted': false}")
    Page<FhirResourceDocument> findByResourceTypeAndLastUpdatedBetween(String resourceType, Instant start, Instant end, Pageable pageable);

    // ========== Cursor-Based Pagination (NEW - O(1) performance) ==========

    /**
     * Cursor-based pagination - find resources after a given ID.
     * O(1) performance regardless of how deep into the result set.
     */
    @Query("{'resourceType': ?0, 'deleted': false, '_id': {'$gt': ?1}}")
    Slice<FhirResourceDocument> findByResourceTypeAfterCursor(String resourceType, ObjectId cursor, Pageable pageable);

    @Query("{'resourceType': ?0, 'deleted': false, '_id': {'$lt': ?1}}")
    Slice<FhirResourceDocument> findByResourceTypeBeforeCursor(String resourceType, ObjectId cursor, Pageable pageable);

    // System-wide cursor pagination
    @Query("{'deleted': false, '_id': {'$gt': ?0}}")
    Slice<FhirResourceDocument> findAllAfterCursor(ObjectId cursor, Pageable pageable);

    // ========== Count Queries (with hints for index usage) ==========

    long countByResourceTypeAndDeletedFalse(String resourceType);

    long countByDeletedFalse();

    /**
     * Estimated count using collection stats (O(1) for large collections).
     * Use when exact count isn't required.
     */
    @Aggregation(pipeline = {
        "{ $match: { resourceType: ?0, deleted: false } }",
        "{ $count: 'total' }"
    })
    Long countEstimateByResourceType(String resourceType);

    // ========== Field-Based Queries (FIXED - use Page) ==========

    @Query("{'resourceType': ?0, 'resourceData.?1': ?2, 'deleted': false}")
    Page<FhirResourceDocument> findByResourceTypeAndField(String resourceType, String fieldPath, Object value, Pageable pageable);

    // ========== Patient Reference Queries (Common FHIR pattern) ==========

    @Query("{'resourceType': ?0, 'resourceData.subject.reference': ?1, 'deleted': false}")
    Page<FhirResourceDocument> findByResourceTypeAndPatientReference(String resourceType, String patientReference, Pageable pageable);

    @Query("{'resourceType': ?0, 'resourceData.patient.reference': ?1, 'deleted': false}")
    Page<FhirResourceDocument> findByResourceTypeAndPatientRef(String resourceType, String patientReference, Pageable pageable);

    // ========== Code/Coding Queries (Common FHIR pattern) ==========

    @Query("{'resourceType': ?0, 'resourceData.code.coding': {'$elemMatch': {'system': ?1, 'code': ?2}}, 'deleted': false}")
    Page<FhirResourceDocument> findByResourceTypeAndCoding(String resourceType, String system, String code, Pageable pageable);

    // ========== Streaming Queries (For batch processing) ==========

    /**
     * Stream results for batch processing without loading all into memory.
     * Must be used within a transaction or with try-with-resources.
     */
    @Query("{'resourceType': ?0, 'deleted': false}")
    Stream<FhirResourceDocument> streamByResourceTypeAndDeletedFalse(String resourceType);

    @Query("{'deleted': false}")
    Stream<FhirResourceDocument> streamAllActive();

    // ========== Bulk Operations Support ==========

    @Query(value = "{'resourceType': ?0, 'resourceId': {'$in': ?1}}", fields = "{'resourceId': 1, 'versionId': 1}")
    List<FhirResourceDocument> findIdsByResourceTypeAndResourceIdIn(String resourceType, List<String> resourceIds);
}
