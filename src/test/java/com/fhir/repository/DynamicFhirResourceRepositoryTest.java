package com.fhir.repository;

import com.fhir.model.FhirResourceDocument;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DynamicFhirResourceRepository Tests")
class DynamicFhirResourceRepositoryTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private IndexOperations indexOperations;

    @InjectMocks
    private DynamicFhirResourceRepository repository;

    private FhirResourceDocument sampleDocument;

    @BeforeEach
    void setUp() {
        sampleDocument = FhirResourceDocument.builder()
                .id("doc-1")
                .resourceType("Patient")
                .resourceId("p-123")
                .resourceJson("{\"resourceType\":\"Patient\",\"id\":\"p-123\"}")
                .resourceData(Document.parse("{\"resourceType\":\"Patient\",\"id\":\"p-123\"}"))
                .versionId(1L)
                .lastUpdated(Instant.now())
                .createdAt(Instant.now())
                .active(true)
                .deleted(false)
                .isCompressed(false)
                .build();
    }

    @Nested
    @DisplayName("Collection Name Tests")
    class CollectionNameTests {

        @Test
        @DisplayName("Should return lowercase collection name for Patient")
        void shouldReturnLowercaseCollectionNameForPatient() {
            assertEquals("patient", repository.getCollectionName("Patient"));
        }

        @Test
        @DisplayName("Should return lowercase collection name for Observation")
        void shouldReturnLowercaseCollectionNameForObservation() {
            assertEquals("observation", repository.getCollectionName("Observation"));
        }

        @Test
        @DisplayName("Should return lowercase collection name for MedicationRequest")
        void shouldReturnLowercaseCollectionNameForMedicationRequest() {
            assertEquals("medicationrequest", repository.getCollectionName("MedicationRequest"));
        }

        @Test
        @DisplayName("Should handle already lowercase resource type")
        void shouldHandleAlreadyLowercaseResourceType() {
            assertEquals("patient", repository.getCollectionName("patient"));
        }
    }

    @Nested
    @DisplayName("Save Tests")
    class SaveTests {

        @Test
        @DisplayName("Should save document to correct collection")
        void shouldSaveDocumentToCorrectCollection() {
            when(mongoTemplate.indexOps("patient")).thenReturn(indexOperations);
            when(mongoTemplate.save(any(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(sampleDocument);

            FhirResourceDocument result = repository.save(sampleDocument);

            assertNotNull(result);
            verify(mongoTemplate).save(sampleDocument, "patient");
        }

        @Test
        @DisplayName("Should save Observation to observation collection")
        void shouldSaveObservationToObservationCollection() {
            FhirResourceDocument obsDoc = FhirResourceDocument.builder()
                    .resourceType("Observation")
                    .resourceId("o-123")
                    .build();

            when(mongoTemplate.indexOps("observation")).thenReturn(indexOperations);
            when(mongoTemplate.save(any(FhirResourceDocument.class), eq("observation")))
                    .thenReturn(obsDoc);

            repository.save(obsDoc);

            verify(mongoTemplate).save(obsDoc, "observation");
        }
    }

    @Nested
    @DisplayName("SaveAll Tests")
    class SaveAllTests {

        @Test
        @DisplayName("Should save multiple documents to respective collections")
        void shouldSaveMultipleDocumentsToRespectiveCollections() {
            FhirResourceDocument patientDoc = FhirResourceDocument.builder()
                    .resourceType("Patient")
                    .resourceId("p-1")
                    .build();

            FhirResourceDocument obsDoc = FhirResourceDocument.builder()
                    .resourceType("Observation")
                    .resourceId("o-1")
                    .build();

            List<FhirResourceDocument> documents = List.of(patientDoc, obsDoc);

            when(mongoTemplate.indexOps(anyString())).thenReturn(indexOperations);
            when(mongoTemplate.save(any(FhirResourceDocument.class), anyString()))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            List<FhirResourceDocument> result = repository.saveAll(documents);

            assertEquals(2, result.size());
            verify(mongoTemplate).save(patientDoc, "patient");
            verify(mongoTemplate).save(obsDoc, "observation");
        }

        @Test
        @DisplayName("Should return empty list when saving empty list")
        void shouldReturnEmptyListWhenSavingEmptyList() {
            List<FhirResourceDocument> result = repository.saveAll(Collections.emptyList());

            assertTrue(result.isEmpty());
            verify(mongoTemplate, never()).save(any(), anyString());
        }
    }

    @Nested
    @DisplayName("Find By Resource ID Tests")
    class FindByResourceIdTests {

        @Test
        @DisplayName("Should find document by resource type and ID")
        void shouldFindDocumentByResourceTypeAndId() {
            when(mongoTemplate.indexOps("patient")).thenReturn(indexOperations);
            when(mongoTemplate.findOne(any(Query.class), eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(sampleDocument);

            Optional<FhirResourceDocument> result = repository.findByResourceTypeAndResourceId("Patient", "p-123");

            assertTrue(result.isPresent());
            assertEquals("p-123", result.get().getResourceId());
        }

        @Test
        @DisplayName("Should return empty when document not found")
        void shouldReturnEmptyWhenDocumentNotFound() {
            when(mongoTemplate.indexOps("patient")).thenReturn(indexOperations);
            when(mongoTemplate.findOne(any(Query.class), eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(null);

            Optional<FhirResourceDocument> result = repository.findByResourceTypeAndResourceId("Patient", "non-existent");

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should find document excluding deleted")
        void shouldFindDocumentExcludingDeleted() {
            when(mongoTemplate.indexOps("patient")).thenReturn(indexOperations);
            when(mongoTemplate.findOne(any(Query.class), eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(sampleDocument);

            Optional<FhirResourceDocument> result = repository.findByResourceTypeAndResourceIdAndDeletedFalse("Patient", "p-123");

            assertTrue(result.isPresent());
        }
    }

    @Nested
    @DisplayName("Exists Tests")
    class ExistsTests {

        @Test
        @DisplayName("Should return true when document exists")
        void shouldReturnTrueWhenDocumentExists() {
            when(mongoTemplate.indexOps("patient")).thenReturn(indexOperations);
            when(mongoTemplate.exists(any(Query.class), eq("patient")))
                    .thenReturn(true);

            assertTrue(repository.existsByResourceTypeAndResourceId("Patient", "p-123"));
        }

        @Test
        @DisplayName("Should return false when document does not exist")
        void shouldReturnFalseWhenDocumentDoesNotExist() {
            when(mongoTemplate.indexOps("patient")).thenReturn(indexOperations);
            when(mongoTemplate.exists(any(Query.class), eq("patient")))
                    .thenReturn(false);

            assertFalse(repository.existsByResourceTypeAndResourceId("Patient", "non-existent"));
        }
    }

    @Nested
    @DisplayName("Delete Tests")
    class DeleteTests {

        @Test
        @DisplayName("Should delete document from correct collection")
        void shouldDeleteDocumentFromCorrectCollection() {
            when(mongoTemplate.indexOps("patient")).thenReturn(indexOperations);

            repository.deleteByResourceTypeAndResourceId("Patient", "p-123");

            verify(mongoTemplate).remove(any(Query.class), eq("patient"));
        }
    }

    @Nested
    @DisplayName("Paginated Query Tests")
    class PaginatedQueryTests {

        @Test
        @DisplayName("Should return paginated results")
        void shouldReturnPaginatedResults() {
            Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "lastUpdated"));
            List<FhirResourceDocument> documents = List.of(sampleDocument);

            when(mongoTemplate.indexOps("patient")).thenReturn(indexOperations);
            when(mongoTemplate.count(any(Query.class), eq("patient"))).thenReturn(1L);
            when(mongoTemplate.find(any(Query.class), eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(documents);

            Page<FhirResourceDocument> result = repository.findByResourceTypeAndDeletedFalse("Patient", pageable);

            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
            assertEquals(1, result.getContent().size());
        }

        @Test
        @DisplayName("Should return slice with next page indicator")
        void shouldReturnSliceWithNextPageIndicator() {
            Pageable pageable = PageRequest.of(0, 2);
            List<FhirResourceDocument> documents = List.of(sampleDocument, sampleDocument, sampleDocument);

            when(mongoTemplate.indexOps("patient")).thenReturn(indexOperations);
            when(mongoTemplate.find(any(Query.class), eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(documents);

            Slice<FhirResourceDocument> result = repository.findSliceByResourceTypeAndDeletedFalse("Patient", pageable);

            assertNotNull(result);
            assertTrue(result.hasNext());
            assertEquals(2, result.getContent().size());
        }

        @Test
        @DisplayName("Should return slice without next page when no more results")
        void shouldReturnSliceWithoutNextPage() {
            Pageable pageable = PageRequest.of(0, 10);
            List<FhirResourceDocument> documents = List.of(sampleDocument);

            when(mongoTemplate.indexOps("patient")).thenReturn(indexOperations);
            when(mongoTemplate.find(any(Query.class), eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(documents);

            Slice<FhirResourceDocument> result = repository.findSliceByResourceTypeAndDeletedFalse("Patient", pageable);

            assertNotNull(result);
            assertFalse(result.hasNext());
        }
    }

    @Nested
    @DisplayName("Cursor-Based Pagination Tests")
    class CursorBasedPaginationTests {

        @Test
        @DisplayName("Should find documents after cursor")
        void shouldFindDocumentsAfterCursor() {
            ObjectId cursor = new ObjectId();
            Pageable pageable = PageRequest.of(0, 10);

            when(mongoTemplate.indexOps("patient")).thenReturn(indexOperations);
            when(mongoTemplate.find(any(Query.class), eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(List.of(sampleDocument));

            Slice<FhirResourceDocument> result = repository.findByResourceTypeAfterCursor("Patient", cursor, pageable);

            assertNotNull(result);
            assertFalse(result.isEmpty());
        }

        @Test
        @DisplayName("Should find documents before cursor")
        void shouldFindDocumentsBeforeCursor() {
            ObjectId cursor = new ObjectId();
            Pageable pageable = PageRequest.of(0, 10);

            when(mongoTemplate.indexOps("patient")).thenReturn(indexOperations);
            when(mongoTemplate.find(any(Query.class), eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(List.of(sampleDocument));

            Slice<FhirResourceDocument> result = repository.findByResourceTypeBeforeCursor("Patient", cursor, pageable);

            assertNotNull(result);
            assertFalse(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Count Tests")
    class CountTests {

        @Test
        @DisplayName("Should count documents by resource type")
        void shouldCountDocumentsByResourceType() {
            when(mongoTemplate.indexOps("patient")).thenReturn(indexOperations);
            when(mongoTemplate.count(any(Query.class), eq("patient"))).thenReturn(42L);

            long count = repository.countByResourceTypeAndDeletedFalse("Patient");

            assertEquals(42L, count);
        }
    }

    @Nested
    @DisplayName("Patient Reference Query Tests")
    class PatientReferenceQueryTests {

        @Test
        @DisplayName("Should find documents by patient reference")
        void shouldFindDocumentsByPatientReference() {
            Pageable pageable = PageRequest.of(0, 20);

            when(mongoTemplate.indexOps("observation")).thenReturn(indexOperations);
            when(mongoTemplate.count(any(Query.class), eq("observation"))).thenReturn(1L);
            when(mongoTemplate.find(any(Query.class), eq(FhirResourceDocument.class), eq("observation")))
                    .thenReturn(List.of(sampleDocument));

            Page<FhirResourceDocument> result = repository.findByResourceTypeAndPatientReference(
                    "Observation", "Patient/p-123", pageable);

            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
        }
    }

    @Nested
    @DisplayName("System-Wide Query Tests")
    class SystemWideQueryTests {

        @Test
        @DisplayName("Should count all resources across collections")
        void shouldCountAllResourcesAcrossCollections() {
            Set<String> collections = Set.of("patient", "observation");
            when(mongoTemplate.getCollectionNames()).thenReturn(collections);
            when(mongoTemplate.count(any(Query.class), eq("patient"))).thenReturn(10L);
            when(mongoTemplate.count(any(Query.class), eq("observation"))).thenReturn(20L);

            long count = repository.countAllByDeletedFalse();

            assertEquals(30L, count);
        }

        @Test
        @DisplayName("Should skip system collections when counting")
        void shouldSkipSystemCollectionsWhenCounting() {
            Set<String> collections = Set.of("patient", "system.indexes", "fhir_resource_history");
            when(mongoTemplate.getCollectionNames()).thenReturn(collections);
            when(mongoTemplate.count(any(Query.class), eq("patient"))).thenReturn(10L);

            long count = repository.countAllByDeletedFalse();

            assertEquals(10L, count);
            verify(mongoTemplate, never()).count(any(Query.class), eq("system.indexes"));
            verify(mongoTemplate, never()).count(any(Query.class), eq("fhir_resource_history"));
        }

        @Test
        @DisplayName("Should get resource type collections")
        void shouldGetResourceTypeCollections() {
            Set<String> allCollections = Set.of("patient", "observation", "system.indexes", "fhir_resource_history");
            when(mongoTemplate.getCollectionNames()).thenReturn(allCollections);

            Set<String> result = repository.getResourceTypeCollections();

            assertEquals(2, result.size());
            assertTrue(result.contains("patient"));
            assertTrue(result.contains("observation"));
            assertFalse(result.contains("system.indexes"));
            assertFalse(result.contains("fhir_resource_history"));
        }
    }
}
