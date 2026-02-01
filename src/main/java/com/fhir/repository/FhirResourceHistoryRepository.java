package com.fhir.repository;

import com.fhir.model.FhirResourceHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface FhirResourceHistoryRepository extends MongoRepository<FhirResourceHistory, String> {

    List<FhirResourceHistory> findByResourceTypeAndResourceIdOrderByVersionIdDesc(String resourceType, String resourceId);

    Page<FhirResourceHistory> findByResourceTypeAndResourceIdOrderByVersionIdDesc(String resourceType, String resourceId, Pageable pageable);

    Optional<FhirResourceHistory> findByResourceTypeAndResourceIdAndVersionId(String resourceType, String resourceId, Long versionId);

    List<FhirResourceHistory> findByResourceTypeAndResourceIdAndTimestampAfterOrderByVersionIdDesc(
            String resourceType, String resourceId, Instant since);

    long countByResourceTypeAndResourceId(String resourceType, String resourceId);

    void deleteByResourceTypeAndResourceId(String resourceType, String resourceId);
}
