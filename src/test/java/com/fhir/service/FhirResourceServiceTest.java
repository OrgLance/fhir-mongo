package com.fhir.service;

import ca.uhn.fhir.context.FhirContext;
import com.fhir.exception.FhirResourceNotFoundException;
import com.fhir.exception.FhirValidationException;
import com.fhir.metrics.FhirMetrics;
import com.fhir.model.FhirResourceDocument;
import com.fhir.model.FhirResourceHistory;
import com.fhir.repository.DynamicFhirResourceRepository;
import com.fhir.repository.FhirResourceHistoryRepository;
import io.micrometer.core.instrument.Timer;
import org.bson.Document;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FhirResourceService Tests")
class FhirResourceServiceTest {

    @Mock
    private DynamicFhirResourceRepository resourceRepository;

    @Mock
    private FhirResourceHistoryRepository historyRepository;

    @Mock
    private FhirMetrics metrics;

    @Mock
    private AuditLogService auditLogService;

    @Spy
    private FhirContext fhirContext = FhirContext.forR4();

    @InjectMocks
    private FhirResourceService resourceService;

    private Patient samplePatient;
    private FhirResourceDocument sampleDocument;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(resourceService, "baseUrl", "http://localhost:8080/fhir");
        ReflectionTestUtils.setField(resourceService, "defaultPageSize", 20);
        ReflectionTestUtils.setField(resourceService, "maxPageSize", 100);
        ReflectionTestUtils.setField(resourceService, "compressionEnabled", true);

        samplePatient = new Patient();
        samplePatient.addName().setFamily("Smith").addGiven("John");
        samplePatient.setGender(Enumerations.AdministrativeGender.MALE);
        samplePatient.setBirthDate(new Date());

        sampleDocument = FhirResourceDocument.builder()
                .id("doc-1")
                .resourceType("Patient")
                .resourceId("p-123")
                .resourceJson("{\"resourceType\":\"Patient\",\"id\":\"p-123\",\"name\":[{\"family\":\"Smith\",\"given\":[\"John\"]}],\"gender\":\"male\"}")
                .resourceData(Document.parse("{\"resourceType\":\"Patient\",\"id\":\"p-123\",\"name\":[{\"family\":\"Smith\",\"given\":[\"John\"]}],\"gender\":\"male\"}"))
                .versionId(1L)
                .lastUpdated(Instant.now())
                .createdAt(Instant.now())
                .active(true)
                .deleted(false)
                .isCompressed(false)
                .build();

        // Setup common mocks
        when(metrics.startTimer()).thenReturn(mock(Timer.Sample.class));
    }

    @Nested
    @DisplayName("Create Resource Tests")
    class CreateResourceTests {

        @Test
        @DisplayName("Should create a Patient resource successfully")
        void shouldCreatePatientSuccessfully() {
            when(resourceRepository.save(any(FhirResourceDocument.class))).thenAnswer(invocation -> {
                FhirResourceDocument doc = invocation.getArgument(0);
                doc.setId("generated-id");
                return doc;
            });

            Patient created = resourceService.create(samplePatient);

            assertNotNull(created);
            assertNotNull(created.getId());
            assertEquals("1", created.getMeta().getVersionId());
            assertNotNull(created.getMeta().getLastUpdated());

            verify(resourceRepository).save(any(FhirResourceDocument.class));
            verify(metrics).recordCreate("Patient");
        }

        @Test
        @DisplayName("Should set metadata on create")
        void shouldSetMetadataOnCreate() {
            ArgumentCaptor<FhirResourceDocument> docCaptor = ArgumentCaptor.forClass(FhirResourceDocument.class);
            when(resourceRepository.save(docCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

            resourceService.create(samplePatient);

            FhirResourceDocument savedDoc = docCaptor.getValue();
            assertEquals("Patient", savedDoc.getResourceType());
            assertEquals(1L, savedDoc.getVersionId());
            assertFalse(savedDoc.getDeleted());
            assertTrue(savedDoc.getActive());
        }

        @Test
        @DisplayName("Should create Observation resource")
        void shouldCreateObservationResource() {
            Observation observation = new Observation();
            observation.setStatus(Observation.ObservationStatus.FINAL);
            observation.getCode().addCoding().setSystem("http://loinc.org").setCode("12345");
            observation.setSubject(new Reference("Patient/p-123"));

            when(resourceRepository.save(any(FhirResourceDocument.class))).thenAnswer(invocation -> {
                FhirResourceDocument doc = invocation.getArgument(0);
                doc.setId("obs-id");
                return doc;
            });

            Observation created = resourceService.create(observation);

            assertNotNull(created);
            verify(resourceRepository).save(any(FhirResourceDocument.class));
            verify(metrics).recordCreate("Observation");
        }

        @Test
        @DisplayName("Should record metrics on create")
        void shouldRecordMetricsOnCreate() {
            when(resourceRepository.save(any(FhirResourceDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

            resourceService.create(samplePatient);

            verify(metrics).startTimer();
            verify(metrics).recordCreate("Patient");
            verify(metrics).recordCreateLatency(any(Timer.Sample.class));
        }
    }

    @Nested
    @DisplayName("Read Resource Tests")
    class ReadResourceTests {

        @Test
        @DisplayName("Should read Patient resource by ID")
        void shouldReadPatientById() {
            when(resourceRepository.findByResourceTypeAndResourceIdAndDeletedFalse("Patient", "p-123"))
                    .thenReturn(Optional.of(sampleDocument));

            Patient patient = resourceService.read("Patient", "p-123");

            assertNotNull(patient);
            assertTrue(patient.getId().contains("p-123"));
            verify(metrics).recordRead("Patient");
        }

        @Test
        @DisplayName("Should throw exception when resource not found")
        void shouldThrowExceptionWhenResourceNotFound() {
            when(resourceRepository.findByResourceTypeAndResourceIdAndDeletedFalse("Patient", "non-existent"))
                    .thenReturn(Optional.empty());

            assertThrows(FhirResourceNotFoundException.class, () ->
                    resourceService.read("Patient", "non-existent"));
        }

        @Test
        @DisplayName("Should decompress compressed resource on read")
        void shouldDecompressCompressedResourceOnRead() {
            String json = "{\"resourceType\":\"Patient\",\"id\":\"p-compressed\",\"name\":[{\"family\":\"Compressed\"}]}";
            FhirResourceDocument compressedDoc = FhirResourceDocument.builder()
                    .id("doc-compressed")
                    .resourceType("Patient")
                    .resourceId("p-compressed")
                    .resourceJson(null)
                    .compressedJson(com.fhir.util.CompressionUtil.compress(json))
                    .isCompressed(true)
                    .versionId(1L)
                    .deleted(false)
                    .build();

            when(resourceRepository.findByResourceTypeAndResourceIdAndDeletedFalse("Patient", "p-compressed"))
                    .thenReturn(Optional.of(compressedDoc));

            Patient patient = resourceService.read("Patient", "p-compressed");

            assertNotNull(patient);
            assertTrue(patient.getId().contains("p-compressed"));
        }
    }

    @Nested
    @DisplayName("Update Resource Tests")
    class UpdateResourceTests {

        @Test
        @DisplayName("Should update existing resource")
        void shouldUpdateExistingResource() {
            when(resourceRepository.findByResourceTypeAndResourceId("Patient", "p-123"))
                    .thenReturn(Optional.of(sampleDocument));
            when(resourceRepository.save(any(FhirResourceDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Patient updatedPatient = new Patient();
            updatedPatient.addName().setFamily("UpdatedSmith").addGiven("John");

            Patient result = resourceService.update("Patient", "p-123", updatedPatient);

            assertNotNull(result);
            assertEquals("2", result.getMeta().getVersionId());
            verify(resourceRepository).save(any(FhirResourceDocument.class));
            verify(metrics).recordUpdate("Patient");
        }

        @Test
        @DisplayName("Should create resource on update if not exists (upsert)")
        void shouldCreateResourceOnUpdateIfNotExists() {
            when(resourceRepository.findByResourceTypeAndResourceId("Patient", "new-id"))
                    .thenReturn(Optional.empty());
            when(resourceRepository.save(any(FhirResourceDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Patient result = resourceService.update("Patient", "new-id", samplePatient);

            assertNotNull(result);
            assertEquals("1", result.getMeta().getVersionId());
            verify(resourceRepository).save(any(FhirResourceDocument.class));
        }

        @Test
        @DisplayName("Should throw exception when updating deleted resource")
        void shouldThrowExceptionWhenUpdatingDeletedResource() {
            FhirResourceDocument deletedDoc = FhirResourceDocument.builder()
                    .id("doc-deleted")
                    .resourceType("Patient")
                    .resourceId("p-deleted")
                    .deleted(true)
                    .versionId(1L)
                    .build();

            when(resourceRepository.findByResourceTypeAndResourceId("Patient", "p-deleted"))
                    .thenReturn(Optional.of(deletedDoc));

            assertThrows(FhirValidationException.class, () ->
                    resourceService.update("Patient", "p-deleted", samplePatient));
        }

        @Test
        @DisplayName("Should increment version on update")
        void shouldIncrementVersionOnUpdate() {
            ArgumentCaptor<FhirResourceDocument> docCaptor = ArgumentCaptor.forClass(FhirResourceDocument.class);
            when(resourceRepository.findByResourceTypeAndResourceId("Patient", "p-123"))
                    .thenReturn(Optional.of(sampleDocument));
            when(resourceRepository.save(docCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

            resourceService.update("Patient", "p-123", samplePatient);

            assertEquals(2L, docCaptor.getValue().getVersionId());
        }
    }

    @Nested
    @DisplayName("Delete Resource Tests")
    class DeleteResourceTests {

        @Test
        @DisplayName("Should soft delete resource")
        void shouldSoftDeleteResource() {
            when(resourceRepository.findByResourceTypeAndResourceIdAndDeletedFalse("Patient", "p-123"))
                    .thenReturn(Optional.of(sampleDocument));
            when(resourceRepository.save(any(FhirResourceDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

            resourceService.delete("Patient", "p-123");

            ArgumentCaptor<FhirResourceDocument> docCaptor = ArgumentCaptor.forClass(FhirResourceDocument.class);
            verify(resourceRepository).save(docCaptor.capture());

            assertTrue(docCaptor.getValue().getDeleted());
            assertFalse(docCaptor.getValue().getActive());
            verify(metrics).recordDelete("Patient");
        }

        @Test
        @DisplayName("Should throw exception when deleting non-existent resource")
        void shouldThrowExceptionWhenDeletingNonExistent() {
            when(resourceRepository.findByResourceTypeAndResourceIdAndDeletedFalse("Patient", "non-existent"))
                    .thenReturn(Optional.empty());

            assertThrows(FhirResourceNotFoundException.class, () ->
                    resourceService.delete("Patient", "non-existent"));
        }

        @Test
        @DisplayName("Should increment version on delete")
        void shouldIncrementVersionOnDelete() {
            ArgumentCaptor<FhirResourceDocument> docCaptor = ArgumentCaptor.forClass(FhirResourceDocument.class);
            when(resourceRepository.findByResourceTypeAndResourceIdAndDeletedFalse("Patient", "p-123"))
                    .thenReturn(Optional.of(sampleDocument));
            when(resourceRepository.save(docCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

            resourceService.delete("Patient", "p-123");

            assertEquals(2L, docCaptor.getValue().getVersionId());
        }
    }

    @Nested
    @DisplayName("Search Resource Tests")
    class SearchResourceTests {

        @Test
        @DisplayName("Should search resources with pagination")
        void shouldSearchResourcesWithPagination() {
            Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "lastUpdated"));
            Page<FhirResourceDocument> mockPage = new PageImpl<>(List.of(sampleDocument), pageable, 1);

            when(resourceRepository.findByResourceTypeAndDeletedFalse("Patient", pageable))
                    .thenReturn(mockPage);

            Bundle result = resourceService.search("Patient", Collections.emptyMap(), 0, 20);

            assertNotNull(result);
            assertEquals(Bundle.BundleType.SEARCHSET, result.getType());
            assertEquals(1, result.getTotal());
            assertEquals(1, result.getEntry().size());
        }

        @Test
        @DisplayName("Should limit page size to max")
        void shouldLimitPageSizeToMax() {
            Pageable expectedPageable = PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "lastUpdated"));
            Page<FhirResourceDocument> mockPage = new PageImpl<>(Collections.emptyList(), expectedPageable, 0);

            when(resourceRepository.findByResourceTypeAndDeletedFalse(eq("Patient"), any(Pageable.class)))
                    .thenReturn(mockPage);

            resourceService.search("Patient", Collections.emptyMap(), 0, 500);

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(resourceRepository).findByResourceTypeAndDeletedFalse(eq("Patient"), pageableCaptor.capture());

            assertEquals(100, pageableCaptor.getValue().getPageSize());
        }

        @Test
        @DisplayName("Should use default page size when count is zero")
        void shouldUseDefaultPageSizeWhenCountIsZero() {
            Pageable expectedPageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "lastUpdated"));
            Page<FhirResourceDocument> mockPage = new PageImpl<>(Collections.emptyList(), expectedPageable, 0);

            when(resourceRepository.findByResourceTypeAndDeletedFalse(eq("Patient"), any(Pageable.class)))
                    .thenReturn(mockPage);

            resourceService.search("Patient", Collections.emptyMap(), 0, 0);

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(resourceRepository).findByResourceTypeAndDeletedFalse(eq("Patient"), pageableCaptor.capture());

            assertEquals(20, pageableCaptor.getValue().getPageSize());
        }
    }

    @Nested
    @DisplayName("Version Read Tests")
    class VersionReadTests {

        @Test
        @DisplayName("Should read specific version of resource")
        void shouldReadSpecificVersion() {
            FhirResourceHistory history = FhirResourceHistory.builder()
                    .resourceType("Patient")
                    .resourceId("p-123")
                    .versionId(2L)
                    .resourceJson("{\"resourceType\":\"Patient\",\"id\":\"p-123\",\"meta\":{\"versionId\":\"2\"}}")
                    .isCompressed(false)
                    .build();

            when(historyRepository.findByResourceTypeAndResourceIdAndVersionId("Patient", "p-123", 2L))
                    .thenReturn(Optional.of(history));

            Patient patient = resourceService.vread("Patient", "p-123", "2");

            assertNotNull(patient);
            assertTrue(patient.getId().contains("p-123"));
        }

        @Test
        @DisplayName("Should throw exception when version not found")
        void shouldThrowExceptionWhenVersionNotFound() {
            when(historyRepository.findByResourceTypeAndResourceIdAndVersionId("Patient", "p-123", 99L))
                    .thenReturn(Optional.empty());

            assertThrows(FhirResourceNotFoundException.class, () ->
                    resourceService.vread("Patient", "p-123", "99"));
        }
    }

    @Nested
    @DisplayName("History Tests")
    class HistoryTests {

        @Test
        @DisplayName("Should get resource history")
        void shouldGetResourceHistory() {
            FhirResourceHistory history1 = FhirResourceHistory.builder()
                    .resourceType("Patient")
                    .resourceId("p-123")
                    .versionId(1L)
                    .resourceJson("{\"resourceType\":\"Patient\",\"id\":\"p-123\"}")
                    .timestamp(Instant.now().minusSeconds(3600))
                    .action("CREATE")
                    .isCompressed(false)
                    .build();

            FhirResourceHistory history2 = FhirResourceHistory.builder()
                    .resourceType("Patient")
                    .resourceId("p-123")
                    .versionId(2L)
                    .resourceJson("{\"resourceType\":\"Patient\",\"id\":\"p-123\"}")
                    .timestamp(Instant.now())
                    .action("UPDATE")
                    .isCompressed(false)
                    .build();

            Pageable pageable = PageRequest.of(0, 20);
            Page<FhirResourceHistory> mockPage = new PageImpl<>(List.of(history2, history1), pageable, 2);

            when(historyRepository.findByResourceTypeAndResourceIdOrderByVersionIdDesc("Patient", "p-123", pageable))
                    .thenReturn(mockPage);

            Bundle result = resourceService.history("Patient", "p-123", 0, 20);

            assertNotNull(result);
            assertEquals(Bundle.BundleType.HISTORY, result.getType());
            assertEquals(2, result.getTotal());
        }
    }

    @Nested
    @DisplayName("Resource Exists Tests")
    class ResourceExistsTests {

        @Test
        @DisplayName("Should return true when resource exists")
        void shouldReturnTrueWhenResourceExists() {
            when(resourceRepository.existsByResourceTypeAndResourceId("Patient", "p-123"))
                    .thenReturn(true);

            assertTrue(resourceService.resourceExists("Patient", "p-123"));
        }

        @Test
        @DisplayName("Should return false when resource does not exist")
        void shouldReturnFalseWhenResourceDoesNotExist() {
            when(resourceRepository.existsByResourceTypeAndResourceId("Patient", "non-existent"))
                    .thenReturn(false);

            assertFalse(resourceService.resourceExists("Patient", "non-existent"));
        }
    }

    @Nested
    @DisplayName("Resource Count Tests")
    class ResourceCountTests {

        @Test
        @DisplayName("Should get resource count")
        void shouldGetResourceCount() {
            when(resourceRepository.countByResourceTypeAndDeletedFalse("Patient"))
                    .thenReturn(42L);

            long count = resourceService.getResourceCount("Patient");

            assertEquals(42L, count);
        }
    }

    @Nested
    @DisplayName("Capability Statement Tests")
    class CapabilityStatementTests {

        @Test
        @DisplayName("Should return capability statement")
        void shouldReturnCapabilityStatement() {
            CapabilityStatement cs = resourceService.getCapabilityStatement();

            assertNotNull(cs);
            assertEquals(Enumerations.PublicationStatus.ACTIVE, cs.getStatus());
            assertEquals(Enumerations.FHIRVersion._4_0_1, cs.getFhirVersion());
            assertTrue(cs.getFormat().stream().anyMatch(f -> f.getValue().equals("json")));
            assertFalse(cs.getRest().isEmpty());
        }

        @Test
        @DisplayName("Should include all resource types in capability statement")
        void shouldIncludeAllResourceTypesInCapabilityStatement() {
            CapabilityStatement cs = resourceService.getCapabilityStatement();

            CapabilityStatement.CapabilityStatementRestComponent rest = cs.getRest().get(0);
            assertFalse(rest.getResource().isEmpty());

            // Verify some key resources are included
            assertTrue(rest.getResource().stream()
                    .anyMatch(r -> r.getType().equals("Patient")));
            assertTrue(rest.getResource().stream()
                    .anyMatch(r -> r.getType().equals("Observation")));
            assertTrue(rest.getResource().stream()
                    .anyMatch(r -> r.getType().equals("Encounter")));
        }
    }
}
