package com.fhir.repository;

import com.fhir.model.FhirResourceDocument;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Dynamic repository that stores each FHIR resource type in its own MongoDB collection.
 *
 * Collection naming: lowercase resource type (e.g., "patient", "observation", "encounter")
 *
 * Benefits:
 * - Better query performance (smaller indexes per collection)
 * - Easier to scale individual resource types
 * - Simpler sharding strategy
 * - Better data isolation
 */
@Repository
public class DynamicFhirResourceRepository {

    @Autowired
    private MongoTemplate mongoTemplate;

    // Track which collections have been initialized with indexes
    private final Set<String> initializedCollections = ConcurrentHashMap.newKeySet();

    /**
     * Get collection name for a resource type.
     * Converts to lowercase for consistency.
     */
    public String getCollectionName(String resourceType) {
        return resourceType.toLowerCase();
    }

    /**
     * Ensure indexes exist for a collection (called lazily on first access)
     */
    private void ensureIndexes(String collectionName) {
        if (initializedCollections.contains(collectionName)) {
            return;
        }

        synchronized (this) {
            if (initializedCollections.contains(collectionName)) {
                return;
            }

            IndexOperations indexOps = mongoTemplate.indexOps(collectionName);

            // Primary lookup index - unique resource identifier
            indexOps.ensureIndex(new Index()
                    .on("resourceId", Sort.Direction.ASC)
                    .unique()
                    .named("resource_id_unique"));

            // Deleted filter index (most common query pattern)
            indexOps.ensureIndex(new Index()
                    .on("deleted", Sort.Direction.ASC)
                    .named("deleted_idx"));

            // Last updated for sorting
            indexOps.ensureIndex(new Index()
                    .on("deleted", Sort.Direction.ASC)
                    .on("lastUpdated", Sort.Direction.DESC)
                    .named("deleted_updated_idx"));

            // Cursor-based pagination support
            indexOps.ensureIndex(new Index()
                    .on("deleted", Sort.Direction.ASC)
                    .on("_id", Sort.Direction.ASC)
                    .named("cursor_pagination_idx"));

            // Patient reference lookup (common FHIR query)
            indexOps.ensureIndex(new Index()
                    .on("resourceData.subject.reference", Sort.Direction.ASC)
                    .on("deleted", Sort.Direction.ASC)
                    .sparse()
                    .named("patient_reference_idx"));

            // Code/coding lookup
            indexOps.ensureIndex(new Index()
                    .on("resourceData.code.coding.system", Sort.Direction.ASC)
                    .on("resourceData.code.coding.code", Sort.Direction.ASC)
                    .on("deleted", Sort.Direction.ASC)
                    .sparse()
                    .named("coding_lookup_idx"));

            initializedCollections.add(collectionName);
        }
    }

    // ========== SAVE ==========

    public FhirResourceDocument save(FhirResourceDocument document) {
        String collectionName = getCollectionName(document.getResourceType());
        ensureIndexes(collectionName);
        return mongoTemplate.save(document, collectionName);
    }

    public List<FhirResourceDocument> saveAll(List<FhirResourceDocument> documents) {
        if (documents.isEmpty()) {
            return documents;
        }

        // Group by resource type and save to respective collections
        Map<String, List<FhirResourceDocument>> byType = new HashMap<>();
        for (FhirResourceDocument doc : documents) {
            byType.computeIfAbsent(doc.getResourceType(), k -> new ArrayList<>()).add(doc);
        }

        List<FhirResourceDocument> saved = new ArrayList<>();
        for (Map.Entry<String, List<FhirResourceDocument>> entry : byType.entrySet()) {
            String collectionName = getCollectionName(entry.getKey());
            ensureIndexes(collectionName);
            for (FhirResourceDocument doc : entry.getValue()) {
                saved.add(mongoTemplate.save(doc, collectionName));
            }
        }

        return saved;
    }

    // ========== FIND BY ID ==========

    public Optional<FhirResourceDocument> findByResourceTypeAndResourceId(String resourceType, String resourceId) {
        String collectionName = getCollectionName(resourceType);
        ensureIndexes(collectionName);

        Query query = new Query(Criteria.where("resourceId").is(resourceId));
        FhirResourceDocument doc = mongoTemplate.findOne(query, FhirResourceDocument.class, collectionName);
        return Optional.ofNullable(doc);
    }

    public Optional<FhirResourceDocument> findByResourceTypeAndResourceIdAndDeletedFalse(String resourceType, String resourceId) {
        String collectionName = getCollectionName(resourceType);
        ensureIndexes(collectionName);

        Query query = new Query(Criteria.where("resourceId").is(resourceId)
                .and("deleted").is(false));
        FhirResourceDocument doc = mongoTemplate.findOne(query, FhirResourceDocument.class, collectionName);
        return Optional.ofNullable(doc);
    }

    // ========== EXISTS ==========

    public boolean existsByResourceTypeAndResourceId(String resourceType, String resourceId) {
        String collectionName = getCollectionName(resourceType);
        ensureIndexes(collectionName);

        Query query = new Query(Criteria.where("resourceId").is(resourceId));
        return mongoTemplate.exists(query, collectionName);
    }

    // ========== DELETE ==========

    public void deleteByResourceTypeAndResourceId(String resourceType, String resourceId) {
        String collectionName = getCollectionName(resourceType);
        ensureIndexes(collectionName);

        Query query = new Query(Criteria.where("resourceId").is(resourceId));
        mongoTemplate.remove(query, collectionName);
    }

    // ========== PAGINATED QUERIES ==========

    public Page<FhirResourceDocument> findByResourceTypeAndDeletedFalse(String resourceType, Pageable pageable) {
        String collectionName = getCollectionName(resourceType);
        ensureIndexes(collectionName);

        Query query = new Query(Criteria.where("deleted").is(false));
        long total = mongoTemplate.count(query, collectionName);

        query.with(pageable);
        List<FhirResourceDocument> content = mongoTemplate.find(query, FhirResourceDocument.class, collectionName);

        return new PageImpl<>(content, pageable, total);
    }

    public Slice<FhirResourceDocument> findSliceByResourceTypeAndDeletedFalse(String resourceType, Pageable pageable) {
        String collectionName = getCollectionName(resourceType);
        ensureIndexes(collectionName);

        Query query = new Query(Criteria.where("deleted").is(false))
                .with(pageable)
                .limit(pageable.getPageSize() + 1); // Fetch one extra to determine if there's a next page

        List<FhirResourceDocument> content = mongoTemplate.find(query, FhirResourceDocument.class, collectionName);

        boolean hasNext = content.size() > pageable.getPageSize();
        if (hasNext) {
            content = content.subList(0, pageable.getPageSize());
        }

        return new SliceImpl<>(content, pageable, hasNext);
    }

    // ========== CURSOR-BASED PAGINATION ==========

    public Slice<FhirResourceDocument> findByResourceTypeAfterCursor(String resourceType, ObjectId cursor, Pageable pageable) {
        String collectionName = getCollectionName(resourceType);
        ensureIndexes(collectionName);

        Query query = new Query(Criteria.where("deleted").is(false)
                .and("_id").gt(cursor))
                .with(Sort.by(Sort.Direction.ASC, "_id"))
                .limit(pageable.getPageSize() + 1);

        List<FhirResourceDocument> content = mongoTemplate.find(query, FhirResourceDocument.class, collectionName);

        boolean hasNext = content.size() > pageable.getPageSize();
        if (hasNext) {
            content = content.subList(0, pageable.getPageSize());
        }

        return new SliceImpl<>(content, pageable, hasNext);
    }

    public Slice<FhirResourceDocument> findByResourceTypeBeforeCursor(String resourceType, ObjectId cursor, Pageable pageable) {
        String collectionName = getCollectionName(resourceType);
        ensureIndexes(collectionName);

        Query query = new Query(Criteria.where("deleted").is(false)
                .and("_id").lt(cursor))
                .with(Sort.by(Sort.Direction.DESC, "_id"))
                .limit(pageable.getPageSize() + 1);

        List<FhirResourceDocument> content = mongoTemplate.find(query, FhirResourceDocument.class, collectionName);

        boolean hasNext = content.size() > pageable.getPageSize();
        if (hasNext) {
            content = content.subList(0, pageable.getPageSize());
        }

        return new SliceImpl<>(content, pageable, hasNext);
    }

    // ========== COUNT ==========

    public long countByResourceTypeAndDeletedFalse(String resourceType) {
        String collectionName = getCollectionName(resourceType);
        ensureIndexes(collectionName);

        Query query = new Query(Criteria.where("deleted").is(false));
        return mongoTemplate.count(query, collectionName);
    }

    // ========== PATIENT REFERENCE QUERIES ==========

    public Page<FhirResourceDocument> findByResourceTypeAndPatientReference(
            String resourceType, String patientReference, Pageable pageable) {
        String collectionName = getCollectionName(resourceType);
        ensureIndexes(collectionName);

        Query query = new Query(Criteria.where("deleted").is(false)
                .and("resourceData.subject.reference").is(patientReference));

        long total = mongoTemplate.count(query, collectionName);
        query.with(pageable);
        List<FhirResourceDocument> content = mongoTemplate.find(query, FhirResourceDocument.class, collectionName);

        return new PageImpl<>(content, pageable, total);
    }

    public Page<FhirResourceDocument> findByResourceTypeAndPatientRef(
            String resourceType, String patientReference, Pageable pageable) {
        String collectionName = getCollectionName(resourceType);
        ensureIndexes(collectionName);

        Query query = new Query(Criteria.where("deleted").is(false)
                .and("resourceData.patient.reference").is(patientReference));

        long total = mongoTemplate.count(query, collectionName);
        query.with(pageable);
        List<FhirResourceDocument> content = mongoTemplate.find(query, FhirResourceDocument.class, collectionName);

        return new PageImpl<>(content, pageable, total);
    }

    // ========== CODE/CODING QUERIES ==========

    public Page<FhirResourceDocument> findByResourceTypeAndCoding(
            String resourceType, String system, String code, Pageable pageable) {
        String collectionName = getCollectionName(resourceType);
        ensureIndexes(collectionName);

        Query query = new Query(Criteria.where("deleted").is(false)
                .and("resourceData.code.coding")
                .elemMatch(Criteria.where("system").is(system).and("code").is(code)));

        long total = mongoTemplate.count(query, collectionName);
        query.with(pageable);
        List<FhirResourceDocument> content = mongoTemplate.find(query, FhirResourceDocument.class, collectionName);

        return new PageImpl<>(content, pageable, total);
    }

    // ========== STREAMING ==========

    public Stream<FhirResourceDocument> streamByResourceTypeAndDeletedFalse(String resourceType) {
        String collectionName = getCollectionName(resourceType);
        ensureIndexes(collectionName);

        Query query = new Query(Criteria.where("deleted").is(false));
        return mongoTemplate.stream(query, FhirResourceDocument.class, collectionName);
    }

    // ========== BULK OPERATIONS ==========

    public List<FhirResourceDocument> findIdsByResourceTypeAndResourceIdIn(
            String resourceType, List<String> resourceIds) {
        String collectionName = getCollectionName(resourceType);
        ensureIndexes(collectionName);

        Query query = new Query(Criteria.where("resourceId").in(resourceIds));
        query.fields().include("resourceId").include("versionId");

        return mongoTemplate.find(query, FhirResourceDocument.class, collectionName);
    }

    // ========== SYSTEM-WIDE QUERIES (across all collections) ==========

    public Page<FhirResourceDocument> findAllByDeletedFalse(Pageable pageable) {
        // Get all collection names that might contain FHIR resources
        Set<String> collectionNames = mongoTemplate.getCollectionNames();

        List<FhirResourceDocument> allDocs = new ArrayList<>();
        long totalCount = 0;

        for (String collectionName : collectionNames) {
            // Skip system collections
            if (collectionName.startsWith("system.") || collectionName.equals("fhir_resource_history")) {
                continue;
            }

            Query countQuery = new Query(Criteria.where("deleted").is(false));
            totalCount += mongoTemplate.count(countQuery, collectionName);
        }

        // For simplicity, we'll fetch from each collection
        // In production, consider using aggregation with $unionWith
        int skip = pageable.getPageNumber() * pageable.getPageSize();
        int limit = pageable.getPageSize();
        int skipped = 0;
        int fetched = 0;

        for (String collectionName : collectionNames) {
            if (collectionName.startsWith("system.") || collectionName.equals("fhir_resource_history")) {
                continue;
            }

            if (fetched >= limit) {
                break;
            }

            Query query = new Query(Criteria.where("deleted").is(false))
                    .with(Sort.by(Sort.Direction.DESC, "lastUpdated"));

            long collectionCount = mongoTemplate.count(query, collectionName);

            if (skipped + collectionCount <= skip) {
                skipped += collectionCount;
                continue;
            }

            int collectionSkip = Math.max(0, skip - skipped);
            int collectionLimit = limit - fetched;

            query.skip(collectionSkip).limit(collectionLimit);
            List<FhirResourceDocument> docs = mongoTemplate.find(query, FhirResourceDocument.class, collectionName);
            allDocs.addAll(docs);
            fetched += docs.size();
            skipped += collectionCount;
        }

        return new PageImpl<>(allDocs, pageable, totalCount);
    }

    public Slice<FhirResourceDocument> findAllAfterCursor(ObjectId cursor, Pageable pageable) {
        // This is more complex with multiple collections
        // For now, return empty - implement if needed
        return new SliceImpl<>(new ArrayList<>(), pageable, false);
    }

    public long countAllByDeletedFalse() {
        Set<String> collectionNames = mongoTemplate.getCollectionNames();
        long totalCount = 0;

        for (String collectionName : collectionNames) {
            if (collectionName.startsWith("system.") || collectionName.equals("fhir_resource_history")) {
                continue;
            }

            Query query = new Query(Criteria.where("deleted").is(false));
            totalCount += mongoTemplate.count(query, collectionName);
        }

        return totalCount;
    }

    // ========== UTILITY ==========

    public Set<String> getResourceTypeCollections() {
        Set<String> collections = new HashSet<>();
        for (String name : mongoTemplate.getCollectionNames()) {
            if (!name.startsWith("system.") && !name.equals("fhir_resource_history")) {
                collections.add(name);
            }
        }
        return collections;
    }
}
