package com.fhir.controller;

import ca.uhn.fhir.context.FhirContext;
import com.fhir.config.TestConfig;
import com.fhir.service.FhirResourceService;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.Enumerations;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FhirSystemController.class)
@Import(TestConfig.class)
@DisplayName("FhirSystemController Tests")
class FhirSystemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FhirResourceService resourceService;

    private final FhirContext fhirContext = FhirContext.forR4();

    private CapabilityStatement capabilityStatement;
    private Bundle searchBundle;

    @BeforeEach
    void setUp() {
        capabilityStatement = new CapabilityStatement();
        capabilityStatement.setStatus(Enumerations.PublicationStatus.ACTIVE);
        capabilityStatement.setFhirVersion(Enumerations.FHIRVersion._4_0_1);

        searchBundle = new Bundle();
        searchBundle.setType(Bundle.BundleType.SEARCHSET);
        searchBundle.setTotal(0);
    }

    @Nested
    @DisplayName("Metadata Tests")
    class MetadataTests {

        @Test
        @DisplayName("Should return capability statement")
        void shouldReturnCapabilityStatement() throws Exception {
            when(resourceService.getCapabilityStatement()).thenReturn(capabilityStatement);

            mockMvc.perform(get("/fhir/metadata")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resourceType").value("CapabilityStatement"))
                    .andExpect(jsonPath("$.status").value("active"));

            verify(resourceService).getCapabilityStatement();
        }
    }

    @Nested
    @DisplayName("Transaction Tests")
    class TransactionTests {

        @Test
        @DisplayName("Should process transaction bundle")
        void shouldProcessTransactionBundle() throws Exception {
            Bundle responseBundle = new Bundle();
            responseBundle.setType(Bundle.BundleType.TRANSACTIONRESPONSE);

            when(resourceService.transaction(any(Bundle.class))).thenReturn(responseBundle);

            String transactionJson = "{\"resourceType\":\"Bundle\",\"type\":\"transaction\",\"entry\":[]}";

            mockMvc.perform(post("/fhir")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(transactionJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resourceType").value("Bundle"))
                    .andExpect(jsonPath("$.type").value("transaction-response"));

            verify(resourceService).transaction(any(Bundle.class));
        }

        @Test
        @DisplayName("Should process batch bundle")
        void shouldProcessBatchBundle() throws Exception {
            Bundle responseBundle = new Bundle();
            responseBundle.setType(Bundle.BundleType.BATCHRESPONSE);

            when(resourceService.transaction(any(Bundle.class))).thenReturn(responseBundle);

            String batchJson = "{\"resourceType\":\"Bundle\",\"type\":\"batch\",\"entry\":[]}";

            mockMvc.perform(post("/fhir")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(batchJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resourceType").value("Bundle"));
        }
    }

    @Nested
    @DisplayName("System Search Tests")
    class SystemSearchTests {

        @Test
        @DisplayName("Should perform system-level search")
        void shouldPerformSystemLevelSearch() throws Exception {
            when(resourceService.searchAll(anyMap(), eq(0), eq(20))).thenReturn(searchBundle);

            mockMvc.perform(get("/fhir")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resourceType").value("Bundle"))
                    .andExpect(jsonPath("$.type").value("searchset"));

            verify(resourceService).searchAll(anyMap(), eq(0), eq(20));
        }

        @Test
        @DisplayName("Should perform system search with pagination")
        void shouldPerformSystemSearchWithPagination() throws Exception {
            when(resourceService.searchAll(anyMap(), eq(1), eq(10))).thenReturn(searchBundle);

            mockMvc.perform(get("/fhir")
                            .param("_page", "1")
                            .param("_count", "10")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());

            verify(resourceService).searchAll(anyMap(), eq(1), eq(10));
        }
    }

    @Nested
    @DisplayName("System History Tests")
    class SystemHistoryTests {

        @Test
        @DisplayName("Should get system-level history")
        void shouldGetSystemLevelHistory() throws Exception {
            when(resourceService.searchAll(anyMap(), eq(0), eq(20))).thenReturn(searchBundle);

            mockMvc.perform(get("/fhir/_history")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resourceType").value("Bundle"));

            verify(resourceService).searchAll(anyMap(), eq(0), eq(20));
        }

        @Test
        @DisplayName("Should get system history with pagination")
        void shouldGetSystemHistoryWithPagination() throws Exception {
            when(resourceService.searchAll(anyMap(), eq(2), eq(50))).thenReturn(searchBundle);

            mockMvc.perform(get("/fhir/_history")
                            .param("_page", "2")
                            .param("_count", "50")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());

            verify(resourceService).searchAll(anyMap(), eq(2), eq(50));
        }
    }
}
