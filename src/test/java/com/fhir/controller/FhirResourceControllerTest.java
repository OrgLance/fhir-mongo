package com.fhir.controller;

import ca.uhn.fhir.context.FhirContext;
import com.fhir.config.TestConfig;
import com.fhir.model.FhirResourceDocument;
import com.fhir.service.FhirResourceService;
import com.fhir.service.FhirSearchService;
import org.bson.Document;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FhirResourceController.class)
@Import(TestConfig.class)
@DisplayName("FhirResourceController Tests")
class FhirResourceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FhirResourceService resourceService;

    @MockBean
    private FhirSearchService searchService;

    private final FhirContext fhirContext = FhirContext.forR4();

    private Patient samplePatient;
    private String samplePatientJson;
    private FhirResourceDocument sampleDocument;

    @BeforeEach
    void setUp() {
        samplePatient = new Patient();
        samplePatient.setId("p-123");
        samplePatient.addName().setFamily("Smith").addGiven("John");
        samplePatient.setGender(Enumerations.AdministrativeGender.MALE);
        samplePatient.setMeta(new Meta().setVersionId("1").setLastUpdated(new Date()));

        samplePatientJson = fhirContext.newJsonParser().encodeResourceToString(samplePatient);

        sampleDocument = FhirResourceDocument.builder()
                .id("doc-1")
                .resourceType("Patient")
                .resourceId("p-123")
                .resourceJson(samplePatientJson)
                .resourceData(Document.parse(samplePatientJson))
                .versionId(1L)
                .lastUpdated(Instant.now())
                .createdAt(Instant.now())
                .active(true)
                .deleted(false)
                .isCompressed(false)
                .build();
    }

    @Nested
    @DisplayName("Create Resource Tests")
    class CreateResourceTests {

        @Test
        @DisplayName("Should create Patient resource successfully")
        void shouldCreatePatientSuccessfully() throws Exception {
            when(resourceService.create(any(Patient.class))).thenReturn(samplePatient);

            String requestJson = "{\"resourceType\":\"Patient\",\"name\":[{\"family\":\"Smith\",\"given\":[\"John\"]}]}";

            mockMvc.perform(post("/fhir/Patient")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"))
                    .andExpect(header().string("ETag", "W/\"1\""))
                    .andExpect(jsonPath("$.resourceType").value("Patient"))
                    .andExpect(jsonPath("$.id").value("p-123"));

            verify(resourceService).create(any(Patient.class));
        }

        @Test
        @DisplayName("Should create Observation resource")
        void shouldCreateObservationResource() throws Exception {
            Observation observation = new Observation();
            observation.setId("o-123");
            observation.setStatus(Observation.ObservationStatus.FINAL);
            observation.setMeta(new Meta().setVersionId("1").setLastUpdated(new Date()));

            when(resourceService.create(any(Observation.class))).thenReturn(observation);

            String requestJson = "{\"resourceType\":\"Observation\",\"status\":\"final\"}";

            mockMvc.perform(post("/fhir/Observation")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.resourceType").value("Observation"));
        }
    }

    @Nested
    @DisplayName("Read Resource Tests")
    class ReadResourceTests {

        @Test
        @DisplayName("Should read Patient by ID")
        void shouldReadPatientById() throws Exception {
            when(resourceService.read("Patient", "p-123")).thenReturn(samplePatient);

            mockMvc.perform(get("/fhir/Patient/p-123")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(header().string("ETag", "W/\"1\""))
                    .andExpect(jsonPath("$.resourceType").value("Patient"))
                    .andExpect(jsonPath("$.id").value("p-123"))
                    .andExpect(jsonPath("$.name[0].family").value("Smith"));

            verify(resourceService).read("Patient", "p-123");
        }

        @Test
        @DisplayName("Should return 404 when resource not found")
        void shouldReturn404WhenResourceNotFound() throws Exception {
            when(resourceService.read("Patient", "non-existent"))
                    .thenThrow(new com.fhir.exception.FhirResourceNotFoundException("Patient", "non-existent"));

            mockMvc.perform(get("/fhir/Patient/non-existent")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Version Read Tests")
    class VersionReadTests {

        @Test
        @DisplayName("Should read specific version of resource")
        void shouldReadSpecificVersion() throws Exception {
            Patient versionedPatient = new Patient();
            versionedPatient.setId("p-123");
            versionedPatient.setMeta(new Meta().setVersionId("2").setLastUpdated(new Date()));

            when(resourceService.vread("Patient", "p-123", "2")).thenReturn(versionedPatient);

            mockMvc.perform(get("/fhir/Patient/p-123/_history/2")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(header().string("ETag", "W/\"2\""));
        }
    }

    @Nested
    @DisplayName("Update Resource Tests")
    class UpdateResourceTests {

        @Test
        @DisplayName("Should update existing resource")
        void shouldUpdateExistingResource() throws Exception {
            Patient updatedPatient = new Patient();
            updatedPatient.setId("p-123");
            updatedPatient.addName().setFamily("UpdatedSmith");
            updatedPatient.setMeta(new Meta().setVersionId("2").setLastUpdated(new Date()));

            when(resourceService.resourceExists("Patient", "p-123")).thenReturn(true);
            when(resourceService.update(eq("Patient"), eq("p-123"), any(Patient.class)))
                    .thenReturn(updatedPatient);

            String requestJson = "{\"resourceType\":\"Patient\",\"name\":[{\"family\":\"UpdatedSmith\"}]}";

            mockMvc.perform(put("/fhir/Patient/p-123")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk())
                    .andExpect(header().string("ETag", "W/\"2\""));

            verify(resourceService).update(eq("Patient"), eq("p-123"), any(Patient.class));
        }

        @Test
        @DisplayName("Should return 201 when creating via update")
        void shouldReturn201WhenCreatingViaUpdate() throws Exception {
            when(resourceService.resourceExists("Patient", "new-id")).thenReturn(false);
            when(resourceService.update(eq("Patient"), eq("new-id"), any(Patient.class)))
                    .thenReturn(samplePatient);

            String requestJson = "{\"resourceType\":\"Patient\",\"name\":[{\"family\":\"New\"}]}";

            mockMvc.perform(put("/fhir/Patient/new-id")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("Delete Resource Tests")
    class DeleteResourceTests {

        @Test
        @DisplayName("Should delete resource")
        void shouldDeleteResource() throws Exception {
            doNothing().when(resourceService).delete("Patient", "p-123");

            mockMvc.perform(delete("/fhir/Patient/p-123"))
                    .andExpect(status().isNoContent());

            verify(resourceService).delete("Patient", "p-123");
        }
    }

    @Nested
    @DisplayName("Search Resource Tests")
    class SearchResourceTests {

        @Test
        @DisplayName("Should search resources without parameters")
        void shouldSearchResourcesWithoutParameters() throws Exception {
            Bundle searchBundle = new Bundle();
            searchBundle.setType(Bundle.BundleType.SEARCHSET);
            searchBundle.setTotal(1);
            searchBundle.addEntry().setResource(samplePatient);

            when(resourceService.search(eq("Patient"), anyMap(), eq(0), eq(20)))
                    .thenReturn(searchBundle);

            mockMvc.perform(get("/fhir/Patient")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resourceType").value("Bundle"))
                    .andExpect(jsonPath("$.type").value("searchset"))
                    .andExpect(jsonPath("$.total").value(1));
        }

        @Test
        @DisplayName("Should search resources with parameters")
        void shouldSearchResourcesWithParameters() throws Exception {
            Page<FhirResourceDocument> page = new PageImpl<>(List.of(sampleDocument));
            when(searchService.search(eq("Patient"), anyMap(), any(Pageable.class)))
                    .thenReturn(page);

            mockMvc.perform(get("/fhir/Patient")
                            .param("name", "Smith")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resourceType").value("Bundle"))
                    .andExpect(jsonPath("$.type").value("searchset"));

            verify(searchService).search(eq("Patient"), anyMap(), any(Pageable.class));
        }

        @Test
        @DisplayName("Should search with pagination parameters")
        void shouldSearchWithPaginationParameters() throws Exception {
            Bundle searchBundle = new Bundle();
            searchBundle.setType(Bundle.BundleType.SEARCHSET);
            searchBundle.setTotal(0);

            when(resourceService.search(eq("Patient"), anyMap(), eq(1), eq(10)))
                    .thenReturn(searchBundle);

            mockMvc.perform(get("/fhir/Patient")
                            .param("_page", "1")
                            .param("_count", "10")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());

            verify(resourceService).search(eq("Patient"), anyMap(), eq(1), eq(10));
        }
    }

    @Nested
    @DisplayName("History Tests")
    class HistoryTests {

        @Test
        @DisplayName("Should get resource history")
        void shouldGetResourceHistory() throws Exception {
            Bundle historyBundle = new Bundle();
            historyBundle.setType(Bundle.BundleType.HISTORY);
            historyBundle.setTotal(2);

            when(resourceService.history("Patient", "p-123", 0, 20))
                    .thenReturn(historyBundle);

            mockMvc.perform(get("/fhir/Patient/p-123/_history")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resourceType").value("Bundle"))
                    .andExpect(jsonPath("$.type").value("history"));

            verify(resourceService).history("Patient", "p-123", 0, 20);
        }

        @Test
        @DisplayName("Should get type-level history")
        void shouldGetTypeLevelHistory() throws Exception {
            Bundle historyBundle = new Bundle();
            historyBundle.setType(Bundle.BundleType.HISTORY);
            historyBundle.setTotal(10);

            when(resourceService.typeHistory("Patient", 0, 20))
                    .thenReturn(historyBundle);

            mockMvc.perform(get("/fhir/Patient/_history")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resourceType").value("Bundle"))
                    .andExpect(jsonPath("$.type").value("history"));

            verify(resourceService).typeHistory("Patient", 0, 20);
        }
    }

    @Nested
    @DisplayName("Various Resource Types Tests")
    class VariousResourceTypesTests {

        @Test
        @DisplayName("Should handle Encounter resource type")
        void shouldHandleEncounterResourceType() throws Exception {
            Encounter encounter = new Encounter();
            encounter.setId("e-123");
            encounter.setStatus(Encounter.EncounterStatus.FINISHED);
            encounter.setMeta(new Meta().setVersionId("1").setLastUpdated(new Date()));

            when(resourceService.read("Encounter", "e-123")).thenReturn(encounter);

            mockMvc.perform(get("/fhir/Encounter/e-123")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resourceType").value("Encounter"));
        }

        @Test
        @DisplayName("Should handle Condition resource type")
        void shouldHandleConditionResourceType() throws Exception {
            Condition condition = new Condition();
            condition.setId("c-123");
            condition.setMeta(new Meta().setVersionId("1").setLastUpdated(new Date()));

            when(resourceService.read("Condition", "c-123")).thenReturn(condition);

            mockMvc.perform(get("/fhir/Condition/c-123")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resourceType").value("Condition"));
        }

        @Test
        @DisplayName("Should handle MedicationRequest resource type")
        void shouldHandleMedicationRequestResourceType() throws Exception {
            MedicationRequest medReq = new MedicationRequest();
            medReq.setId("mr-123");
            medReq.setStatus(MedicationRequest.MedicationRequestStatus.ACTIVE);
            medReq.setIntent(MedicationRequest.MedicationRequestIntent.ORDER);
            medReq.setMeta(new Meta().setVersionId("1").setLastUpdated(new Date()));

            when(resourceService.read("MedicationRequest", "mr-123")).thenReturn(medReq);

            mockMvc.perform(get("/fhir/MedicationRequest/mr-123")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resourceType").value("MedicationRequest"));
        }
    }
}
