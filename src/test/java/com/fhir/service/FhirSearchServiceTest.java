package com.fhir.service;

import com.fhir.model.FhirResourceDocument;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FhirSearchService Tests")
class FhirSearchServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private FhirSearchService searchService;

    private FhirResourceDocument samplePatient;
    private FhirResourceDocument sampleObservation;

    @BeforeEach
    void setUp() {
        samplePatient = FhirResourceDocument.builder()
                .id("patient-1")
                .resourceType("Patient")
                .resourceId("p-123")
                .resourceJson("{\"resourceType\":\"Patient\",\"id\":\"p-123\",\"name\":[{\"family\":\"Smith\",\"given\":[\"John\"]}]}")
                .resourceData(Document.parse("{\"resourceType\":\"Patient\",\"id\":\"p-123\",\"name\":[{\"family\":\"Smith\",\"given\":[\"John\"]}]}"))
                .versionId(1L)
                .lastUpdated(Instant.now())
                .createdAt(Instant.now())
                .active(true)
                .deleted(false)
                .isCompressed(false)
                .build();

        sampleObservation = FhirResourceDocument.builder()
                .id("obs-1")
                .resourceType("Observation")
                .resourceId("o-456")
                .resourceJson("{\"resourceType\":\"Observation\",\"id\":\"o-456\",\"subject\":{\"reference\":\"Patient/p-123\"}}")
                .resourceData(Document.parse("{\"resourceType\":\"Observation\",\"id\":\"o-456\",\"subject\":{\"reference\":\"Patient/p-123\"}}"))
                .versionId(1L)
                .lastUpdated(Instant.now())
                .createdAt(Instant.now())
                .active(true)
                .deleted(false)
                .isCompressed(false)
                .build();
    }

    @Nested
    @DisplayName("Basic Search Tests")
    class BasicSearchTests {

        @Test
        @DisplayName("Should search with no parameters and return results")
        void shouldSearchWithNoParameters() {
            Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "lastUpdated"));
            List<FhirResourceDocument> results = List.of(samplePatient);

            when(mongoTemplate.find(any(Query.class), eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(results);
            when(mongoTemplate.count(any(Query.class), eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(1L);

            Page<FhirResourceDocument> page = searchService.search("Patient", Collections.emptyMap(), pageable);

            assertNotNull(page);
            assertEquals(1, page.getTotalElements());
            assertEquals(1, page.getContent().size());
            assertEquals("p-123", page.getContent().get(0).getResourceId());

            verify(mongoTemplate).find(any(Query.class), eq(FhirResourceDocument.class), eq("patient"));
        }

        @Test
        @DisplayName("Should use lowercase collection name")
        void shouldUseLowercaseCollectionName() {
            Pageable pageable = PageRequest.of(0, 20);

            when(mongoTemplate.find(any(Query.class), eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(List.of());
            when(mongoTemplate.count(any(Query.class), eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(0L);

            searchService.search("Patient", Collections.emptyMap(), pageable);

            verify(mongoTemplate).find(any(Query.class), eq(FhirResourceDocument.class), eq("patient"));
        }

        @Test
        @DisplayName("Should handle Observation resource type")
        void shouldHandleObservationResourceType() {
            Pageable pageable = PageRequest.of(0, 20);
            List<FhirResourceDocument> results = List.of(sampleObservation);

            when(mongoTemplate.find(any(Query.class), eq(FhirResourceDocument.class), eq("observation")))
                    .thenReturn(results);
            when(mongoTemplate.count(any(Query.class), eq(FhirResourceDocument.class), eq("observation")))
                    .thenReturn(1L);

            Page<FhirResourceDocument> page = searchService.search("Observation", Collections.emptyMap(), pageable);

            assertEquals(1, page.getContent().size());
            verify(mongoTemplate).find(any(Query.class), eq(FhirResourceDocument.class), eq("observation"));
        }

        @Test
        @DisplayName("Should return empty page when no results")
        void shouldReturnEmptyPageWhenNoResults() {
            Pageable pageable = PageRequest.of(0, 20);

            when(mongoTemplate.find(any(Query.class), eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(Collections.emptyList());
            when(mongoTemplate.count(any(Query.class), eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(0L);

            Page<FhirResourceDocument> page = searchService.search("Patient", Collections.emptyMap(), pageable);

            assertTrue(page.isEmpty());
            assertEquals(0, page.getTotalElements());
        }
    }

    @Nested
    @DisplayName("Search Parameter Tests")
    class SearchParameterTests {

        @Test
        @DisplayName("Should search by _id parameter")
        void shouldSearchByIdParameter() {
            Pageable pageable = PageRequest.of(0, 20);
            Map<String, String> params = Map.of("_id", "p-123");

            when(mongoTemplate.find(any(Query.class), eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(List.of(samplePatient));
            when(mongoTemplate.count(any(Query.class), eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(1L);

            Page<FhirResourceDocument> page = searchService.search("Patient", params, pageable);

            assertNotNull(page);
            verify(mongoTemplate).find(any(Query.class), eq(FhirResourceDocument.class), eq("patient"));
        }

        @Test
        @DisplayName("Should search by name parameter for Patient")
        void shouldSearchByNameParameter() {
            Pageable pageable = PageRequest.of(0, 20);
            Map<String, String> params = Map.of("name", "Smith");

            when(mongoTemplate.find(any(Query.class), eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(List.of(samplePatient));
            when(mongoTemplate.count(any(Query.class), eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(1L);

            Page<FhirResourceDocument> page = searchService.search("Patient", params, pageable);

            assertNotNull(page);
            assertEquals(1, page.getContent().size());
        }

        @Test
        @DisplayName("Should search by patient reference for Observation")
        void shouldSearchByPatientReference() {
            Pageable pageable = PageRequest.of(0, 20);
            Map<String, String> params = Map.of("patient", "Patient/p-123");

            when(mongoTemplate.find(any(Query.class), eq(FhirResourceDocument.class), eq("observation")))
                    .thenReturn(List.of(sampleObservation));
            when(mongoTemplate.count(any(Query.class), eq(FhirResourceDocument.class), eq("observation")))
                    .thenReturn(1L);

            Page<FhirResourceDocument> page = searchService.search("Observation", params, pageable);

            assertNotNull(page);
            assertEquals(1, page.getContent().size());
        }

        @Test
        @DisplayName("Should search with multiple parameters")
        void shouldSearchWithMultipleParameters() {
            Pageable pageable = PageRequest.of(0, 20);
            Map<String, String> params = new HashMap<>();
            params.put("family", "Smith");
            params.put("given", "John");

            when(mongoTemplate.find(any(Query.class), eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(List.of(samplePatient));
            when(mongoTemplate.count(any(Query.class), eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(1L);

            Page<FhirResourceDocument> page = searchService.search("Patient", params, pageable);

            assertNotNull(page);
        }

        @Test
        @DisplayName("Should handle _lastUpdated parameter")
        void shouldHandleLastUpdatedParameter() {
            Pageable pageable = PageRequest.of(0, 20);
            Map<String, String> params = Map.of("_lastUpdated", "gt2024-01-01");

            when(mongoTemplate.find(any(Query.class), eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(List.of(samplePatient));
            when(mongoTemplate.count(any(Query.class), eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(1L);

            Page<FhirResourceDocument> page = searchService.search("Patient", params, pageable);

            assertNotNull(page);
        }

        @Test
        @DisplayName("Should skip pagination parameters")
        void shouldSkipPaginationParameters() {
            Pageable pageable = PageRequest.of(0, 20);
            Map<String, String> params = new HashMap<>();
            params.put("_page", "0");
            params.put("_count", "20");
            params.put("_sort", "lastUpdated");

            when(mongoTemplate.find(any(Query.class), eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(List.of(samplePatient));
            when(mongoTemplate.count(any(Query.class), eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(1L);

            Page<FhirResourceDocument> page = searchService.search("Patient", params, pageable);

            assertNotNull(page);
        }
    }

    @Nested
    @DisplayName("Search Modifier Tests")
    class SearchModifierTests {

        @Test
        @DisplayName("Should handle :exact modifier")
        void shouldHandleExactModifier() {
            Pageable pageable = PageRequest.of(0, 20);
            Map<String, String> params = Map.of("name:exact", "Smith");

            when(mongoTemplate.find(any(Query.class), eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(List.of(samplePatient));
            when(mongoTemplate.count(any(Query.class), eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(1L);

            Page<FhirResourceDocument> page = searchService.search("Patient", params, pageable);

            assertNotNull(page);
        }

        @Test
        @DisplayName("Should handle :contains modifier")
        void shouldHandleContainsModifier() {
            Pageable pageable = PageRequest.of(0, 20);
            Map<String, String> params = Map.of("name:contains", "mit");

            when(mongoTemplate.find(any(Query.class), eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(List.of(samplePatient));
            when(mongoTemplate.count(any(Query.class), eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(1L);

            Page<FhirResourceDocument> page = searchService.search("Patient", params, pageable);

            assertNotNull(page);
        }

        @Test
        @DisplayName("Should handle :missing modifier")
        void shouldHandleMissingModifier() {
            Pageable pageable = PageRequest.of(0, 20);
            Map<String, String> params = Map.of("phone:missing", "true");

            when(mongoTemplate.find(any(Query.class), eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(Collections.emptyList());
            when(mongoTemplate.count(any(Query.class), eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(0L);

            Page<FhirResourceDocument> page = searchService.search("Patient", params, pageable);

            assertNotNull(page);
        }

        @Test
        @DisplayName("Should handle :not modifier")
        void shouldHandleNotModifier() {
            Pageable pageable = PageRequest.of(0, 20);
            Map<String, String> params = Map.of("gender:not", "male");

            when(mongoTemplate.find(any(Query.class), eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(List.of(samplePatient));
            when(mongoTemplate.count(any(Query.class), eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(1L);

            Page<FhirResourceDocument> page = searchService.search("Patient", params, pageable);

            assertNotNull(page);
        }

        @Test
        @DisplayName("Should handle comma-separated values (OR)")
        void shouldHandleCommaSeparatedValues() {
            Pageable pageable = PageRequest.of(0, 20);
            Map<String, String> params = Map.of("gender", "male,female");

            when(mongoTemplate.find(any(Query.class), eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(List.of(samplePatient));
            when(mongoTemplate.count(any(Query.class), eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(1L);

            Page<FhirResourceDocument> page = searchService.search("Patient", params, pageable);

            assertNotNull(page);
        }
    }

    @Nested
    @DisplayName("Date Search Tests")
    class DateSearchTests {

        @Test
        @DisplayName("Should handle date with eq prefix")
        void shouldHandleDateWithEqPrefix() {
            Pageable pageable = PageRequest.of(0, 20);
            Map<String, String> params = Map.of("_lastUpdated", "eq2024-01-01");

            when(mongoTemplate.find(any(Query.class), eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(List.of());
            when(mongoTemplate.count(any(Query.class), eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(0L);

            Page<FhirResourceDocument> page = searchService.search("Patient", params, pageable);

            assertNotNull(page);
        }

        @Test
        @DisplayName("Should handle date with gt prefix")
        void shouldHandleDateWithGtPrefix() {
            Pageable pageable = PageRequest.of(0, 20);
            Map<String, String> params = Map.of("_lastUpdated", "gt2024-01-01");

            when(mongoTemplate.find(any(Query.class), eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(List.of(samplePatient));
            when(mongoTemplate.count(any(Query.class), eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(1L);

            Page<FhirResourceDocument> page = searchService.search("Patient", params, pageable);

            assertNotNull(page);
        }

        @Test
        @DisplayName("Should handle date with lt prefix")
        void shouldHandleDateWithLtPrefix() {
            Pageable pageable = PageRequest.of(0, 20);
            Map<String, String> params = Map.of("_lastUpdated", "lt2025-12-31");

            when(mongoTemplate.find(any(Query.class), eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(List.of(samplePatient));
            when(mongoTemplate.count(any(Query.class), eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(1L);

            Page<FhirResourceDocument> page = searchService.search("Patient", params, pageable);

            assertNotNull(page);
        }

        @Test
        @DisplayName("Should handle year-only date")
        void shouldHandleYearOnlyDate() {
            Pageable pageable = PageRequest.of(0, 20);
            Map<String, String> params = Map.of("_lastUpdated", "2024");

            when(mongoTemplate.find(any(Query.class), eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(List.of());
            when(mongoTemplate.count(any(Query.class), eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(0L);

            Page<FhirResourceDocument> page = searchService.search("Patient", params, pageable);

            assertNotNull(page);
        }

        @Test
        @DisplayName("Should handle year-month date")
        void shouldHandleYearMonthDate() {
            Pageable pageable = PageRequest.of(0, 20);
            Map<String, String> params = Map.of("_lastUpdated", "2024-01");

            when(mongoTemplate.find(any(Query.class), eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(List.of());
            when(mongoTemplate.count(any(Query.class), eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(0L);

            Page<FhirResourceDocument> page = searchService.search("Patient", params, pageable);

            assertNotNull(page);
        }
    }

    @Nested
    @DisplayName("Resource Type Specific Tests")
    class ResourceTypeSpecificTests {

        @Test
        @DisplayName("Should search Encounter by patient")
        void shouldSearchEncounterByPatient() {
            Pageable pageable = PageRequest.of(0, 20);
            Map<String, String> params = Map.of("patient", "Patient/p-123");

            when(mongoTemplate.find(any(Query.class), eq(FhirResourceDocument.class), eq("encounter")))
                    .thenReturn(List.of());
            when(mongoTemplate.count(any(Query.class), eq(FhirResourceDocument.class), eq("encounter")))
                    .thenReturn(0L);

            Page<FhirResourceDocument> page = searchService.search("Encounter", params, pageable);

            assertNotNull(page);
            verify(mongoTemplate).find(any(Query.class), eq(FhirResourceDocument.class), eq("encounter"));
        }

        @Test
        @DisplayName("Should search Condition by clinical-status")
        void shouldSearchConditionByClinicalStatus() {
            Pageable pageable = PageRequest.of(0, 20);
            Map<String, String> params = Map.of("clinical-status", "active");

            when(mongoTemplate.find(any(Query.class), eq(FhirResourceDocument.class), eq("condition")))
                    .thenReturn(List.of());
            when(mongoTemplate.count(any(Query.class), eq(FhirResourceDocument.class), eq("condition")))
                    .thenReturn(0L);

            Page<FhirResourceDocument> page = searchService.search("Condition", params, pageable);

            assertNotNull(page);
        }

        @Test
        @DisplayName("Should search MedicationRequest by status")
        void shouldSearchMedicationRequestByStatus() {
            Pageable pageable = PageRequest.of(0, 20);
            Map<String, String> params = Map.of("status", "active");

            when(mongoTemplate.find(any(Query.class), eq(FhirResourceDocument.class), eq("medicationrequest")))
                    .thenReturn(List.of());
            when(mongoTemplate.count(any(Query.class), eq(FhirResourceDocument.class), eq("medicationrequest")))
                    .thenReturn(0L);

            Page<FhirResourceDocument> page = searchService.search("MedicationRequest", params, pageable);

            assertNotNull(page);
        }

        @Test
        @DisplayName("Should search Organization by name")
        void shouldSearchOrganizationByName() {
            Pageable pageable = PageRequest.of(0, 20);
            Map<String, String> params = Map.of("name", "Hospital");

            when(mongoTemplate.find(any(Query.class), eq(FhirResourceDocument.class), eq("organization")))
                    .thenReturn(List.of());
            when(mongoTemplate.count(any(Query.class), eq(FhirResourceDocument.class), eq("organization")))
                    .thenReturn(0L);

            Page<FhirResourceDocument> page = searchService.search("Organization", params, pageable);

            assertNotNull(page);
        }
    }
}
