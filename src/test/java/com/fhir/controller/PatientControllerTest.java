package com.fhir.controller;

import ca.uhn.fhir.context.FhirContext;
import com.fhir.config.TestConfig;
import com.fhir.model.CursorPage;
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

@WebMvcTest(PatientController.class)
@Import(TestConfig.class)
@DisplayName("PatientController Tests")
class PatientControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FhirResourceService resourceService;

    @MockBean
    private FhirSearchService searchService;

    private final FhirContext fhirContext = FhirContext.forR4();

    private Patient samplePatient;
    private FhirResourceDocument sampleDocument;

    @BeforeEach
    void setUp() {
        samplePatient = new Patient();
        samplePatient.setId("p-123");
        samplePatient.addName().setFamily("Smith").addGiven("John");
        samplePatient.setGender(Enumerations.AdministrativeGender.MALE);
        samplePatient.setBirthDate(new Date());
        samplePatient.setMeta(new Meta().setVersionId("1").setLastUpdated(new Date()));

        String patientJson = fhirContext.newJsonParser().encodeResourceToString(samplePatient);
        sampleDocument = FhirResourceDocument.builder()
                .id("doc-1")
                .resourceType("Patient")
                .resourceId("p-123")
                .resourceJson(patientJson)
                .resourceData(Document.parse(patientJson))
                .versionId(1L)
                .lastUpdated(Instant.now())
                .deleted(false)
                .isCompressed(false)
                .build();
    }

    @Nested
    @DisplayName("Create Patient Tests")
    class CreatePatientTests {

        @Test
        @DisplayName("Should create patient successfully")
        void shouldCreatePatientSuccessfully() throws Exception {
            when(resourceService.create(any(Patient.class))).thenReturn(samplePatient);

            String requestJson = "{\"resourceType\":\"Patient\",\"name\":[{\"family\":\"Smith\",\"given\":[\"John\"]}]}";

            mockMvc.perform(post("/api/patients")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"))
                    .andExpect(jsonPath("$.resourceType").value("Patient"))
                    .andExpect(jsonPath("$.id").value("p-123"));

            verify(resourceService).create(any(Patient.class));
        }
    }

    @Nested
    @DisplayName("Read Patient Tests")
    class ReadPatientTests {

        @Test
        @DisplayName("Should read patient by ID")
        void shouldReadPatientById() throws Exception {
            when(resourceService.read("Patient", "p-123")).thenReturn(samplePatient);

            mockMvc.perform(get("/api/patients/p-123")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(header().string("ETag", "W/\"1\""))
                    .andExpect(jsonPath("$.resourceType").value("Patient"))
                    .andExpect(jsonPath("$.id").value("p-123"));

            verify(resourceService).read("Patient", "p-123");
        }
    }

    @Nested
    @DisplayName("Search Patient Tests")
    class SearchPatientTests {

        @Test
        @DisplayName("Should search all patients without parameters")
        void shouldSearchAllPatientsWithoutParameters() throws Exception {
            CursorPage<FhirResourceDocument> cursorPage = CursorPage.of(
                    List.of(sampleDocument), false, null);

            when(resourceService.searchWithCursor(eq("Patient"), isNull(), eq(20)))
                    .thenReturn(cursorPage);

            mockMvc.perform(get("/api/patients")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resourceType").value("Bundle"))
                    .andExpect(jsonPath("$.type").value("searchset"));

            verify(resourceService).searchWithCursor(eq("Patient"), isNull(), eq(20));
        }

        @Test
        @DisplayName("Should search patients with name parameter")
        void shouldSearchPatientsWithNameParameter() throws Exception {
            CursorPage<FhirResourceDocument> cursorPage = CursorPage.of(
                    List.of(sampleDocument), false, null);

            when(searchService.searchWithCursor(eq("Patient"), anyMap(), isNull(), eq(20)))
                    .thenReturn(cursorPage);

            mockMvc.perform(get("/api/patients")
                            .param("name", "Smith")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resourceType").value("Bundle"));

            verify(searchService).searchWithCursor(eq("Patient"), anyMap(), isNull(), eq(20));
        }

        @Test
        @DisplayName("Should search patients with gender parameter")
        void shouldSearchPatientsWithGenderParameter() throws Exception {
            CursorPage<FhirResourceDocument> cursorPage = CursorPage.of(
                    List.of(sampleDocument), false, null);

            when(searchService.searchWithCursor(eq("Patient"), anyMap(), isNull(), eq(20)))
                    .thenReturn(cursorPage);

            mockMvc.perform(get("/api/patients")
                            .param("gender", "male")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resourceType").value("Bundle"));
        }

        @Test
        @DisplayName("Should search patients with birthdate parameter")
        void shouldSearchPatientsWithBirthdateParameter() throws Exception {
            CursorPage<FhirResourceDocument> cursorPage = CursorPage.of(
                    List.of(sampleDocument), false, null);

            when(searchService.searchWithCursor(eq("Patient"), anyMap(), isNull(), eq(20)))
                    .thenReturn(cursorPage);

            mockMvc.perform(get("/api/patients")
                            .param("birthdate", "1990-01-01")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resourceType").value("Bundle"));
        }

        @Test
        @DisplayName("Should search with pagination")
        void shouldSearchWithPagination() throws Exception {
            CursorPage<FhirResourceDocument> cursorPage = CursorPage.of(
                    Collections.emptyList(), false, null);

            when(resourceService.searchWithCursor(eq("Patient"), eq("cursor123"), eq(10)))
                    .thenReturn(cursorPage);

            mockMvc.perform(get("/api/patients")
                            .param("_cursor", "cursor123")
                            .param("_count", "10")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());

            verify(resourceService).searchWithCursor(eq("Patient"), eq("cursor123"), eq(10));
        }
    }

    @Nested
    @DisplayName("Update Patient Tests")
    class UpdatePatientTests {

        @Test
        @DisplayName("Should update existing patient")
        void shouldUpdateExistingPatient() throws Exception {
            Patient updatedPatient = new Patient();
            updatedPatient.setId("p-123");
            updatedPatient.addName().setFamily("UpdatedSmith");
            updatedPatient.setMeta(new Meta().setVersionId("2").setLastUpdated(new Date()));

            when(resourceService.resourceExists("Patient", "p-123")).thenReturn(true);
            when(resourceService.update(eq("Patient"), eq("p-123"), any(Patient.class)))
                    .thenReturn(updatedPatient);

            String requestJson = "{\"resourceType\":\"Patient\",\"name\":[{\"family\":\"UpdatedSmith\"}]}";

            mockMvc.perform(put("/api/patients/p-123")
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

            mockMvc.perform(put("/api/patients/new-id")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("Delete Patient Tests")
    class DeletePatientTests {

        @Test
        @DisplayName("Should delete patient")
        void shouldDeletePatient() throws Exception {
            doNothing().when(resourceService).delete("Patient", "p-123");

            mockMvc.perform(delete("/api/patients/p-123"))
                    .andExpect(status().isNoContent());

            verify(resourceService).delete("Patient", "p-123");
        }
    }

    @Nested
    @DisplayName("Patient History Tests")
    class PatientHistoryTests {

        @Test
        @DisplayName("Should get patient history")
        void shouldGetPatientHistory() throws Exception {
            Bundle historyBundle = new Bundle();
            historyBundle.setType(Bundle.BundleType.HISTORY);
            historyBundle.setTotal(2);

            when(resourceService.history("Patient", "p-123", 0, 20))
                    .thenReturn(historyBundle);

            mockMvc.perform(get("/api/patients/p-123/_history")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resourceType").value("Bundle"))
                    .andExpect(jsonPath("$.type").value("history"));

            verify(resourceService).history("Patient", "p-123", 0, 20);
        }

        @Test
        @DisplayName("Should get specific version of patient")
        void shouldGetSpecificVersionOfPatient() throws Exception {
            Patient versionedPatient = new Patient();
            versionedPatient.setId("p-123");
            versionedPatient.setMeta(new Meta().setVersionId("2").setLastUpdated(new Date()));

            when(resourceService.vread("Patient", "p-123", "2")).thenReturn(versionedPatient);

            mockMvc.perform(get("/api/patients/p-123/_history/2")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(header().string("ETag", "W/\"2\""));

            verify(resourceService).vread("Patient", "p-123", "2");
        }
    }
}
