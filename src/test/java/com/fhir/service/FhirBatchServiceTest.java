package com.fhir.service;

import ca.uhn.fhir.context.FhirContext;
import com.fhir.metrics.FhirMetrics;
import com.fhir.model.FhirResourceDocument;
import com.fhir.model.FhirResourceHistory;
import com.mongodb.bulk.BulkWriteResult;
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
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FhirBatchService Tests")
class FhirBatchServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private FhirMetrics metrics;

    @Mock
    private BulkOperations bulkOperations;

    @Mock
    private BulkWriteResult bulkWriteResult;

    @Spy
    private FhirContext fhirContext = FhirContext.forR4();

    @InjectMocks
    private FhirBatchService batchService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(batchService, "batchSize", 1000);
        ReflectionTestUtils.setField(batchService, "baseUrl", "http://localhost:8080/fhir");
    }

    @Nested
    @DisplayName("Bulk Insert Tests")
    class BulkInsertTests {

        @Test
        @DisplayName("Should bulk insert documents to correct collections")
        void shouldBulkInsertDocumentsToCorrectCollections() {
            FhirResourceDocument patientDoc = FhirResourceDocument.builder()
                    .resourceType("Patient")
                    .resourceId("p-1")
                    .resourceJson("{\"resourceType\":\"Patient\",\"id\":\"p-1\"}")
                    .versionId(1L)
                    .build();

            FhirResourceDocument obsDoc = FhirResourceDocument.builder()
                    .resourceType("Observation")
                    .resourceId("o-1")
                    .resourceJson("{\"resourceType\":\"Observation\",\"id\":\"o-1\"}")
                    .versionId(1L)
                    .build();

            List<FhirResourceDocument> documents = List.of(patientDoc, obsDoc);

            when(mongoTemplate.bulkOps(eq(BulkOperations.BulkMode.UNORDERED),
                    eq(FhirResourceDocument.class), anyString()))
                    .thenReturn(bulkOperations);
            when(bulkOperations.execute()).thenReturn(bulkWriteResult);
            when(bulkWriteResult.getInsertedCount()).thenReturn(1);

            BulkWriteResult result = batchService.bulkInsert(documents);

            assertNotNull(result);
            verify(mongoTemplate).bulkOps(BulkOperations.BulkMode.UNORDERED,
                    FhirResourceDocument.class, "patient");
            verify(mongoTemplate).bulkOps(BulkOperations.BulkMode.UNORDERED,
                    FhirResourceDocument.class, "observation");
            verify(metrics).recordBulkOperation("insert", 2);
        }

        @Test
        @DisplayName("Should return null for empty list")
        void shouldReturnNullForEmptyList() {
            BulkWriteResult result = batchService.bulkInsert(Collections.emptyList());

            assertNull(result);
            verify(mongoTemplate, never()).bulkOps(any(), any(Class.class), anyString());
        }

        @Test
        @DisplayName("Should return null for null list")
        void shouldReturnNullForNullList() {
            BulkWriteResult result = batchService.bulkInsert(null);

            assertNull(result);
            verify(mongoTemplate, never()).bulkOps(any(), any(Class.class), anyString());
        }

        @Test
        @DisplayName("Should group documents by resource type")
        void shouldGroupDocumentsByResourceType() {
            FhirResourceDocument patient1 = FhirResourceDocument.builder()
                    .resourceType("Patient")
                    .resourceId("p-1")
                    .resourceJson("{}")
                    .build();
            FhirResourceDocument patient2 = FhirResourceDocument.builder()
                    .resourceType("Patient")
                    .resourceId("p-2")
                    .resourceJson("{}")
                    .build();

            List<FhirResourceDocument> documents = List.of(patient1, patient2);

            when(mongoTemplate.bulkOps(eq(BulkOperations.BulkMode.UNORDERED),
                    eq(FhirResourceDocument.class), eq("patient")))
                    .thenReturn(bulkOperations);
            when(bulkOperations.execute()).thenReturn(bulkWriteResult);
            when(bulkWriteResult.getInsertedCount()).thenReturn(2);

            batchService.bulkInsert(documents);

            // Should only create one bulk operation for patients
            verify(mongoTemplate, times(1)).bulkOps(any(), any(Class.class), anyString());
            verify(bulkOperations, times(2)).insert(any(FhirResourceDocument.class));
        }
    }

    @Nested
    @DisplayName("Bulk Insert History Async Tests")
    class BulkInsertHistoryAsyncTests {

        @Test
        @DisplayName("Should insert history records asynchronously")
        void shouldInsertHistoryRecordsAsynchronously() {
            FhirResourceHistory history1 = FhirResourceHistory.builder()
                    .resourceType("Patient")
                    .resourceId("p-1")
                    .versionId(1L)
                    .resourceJson("{}")
                    .action("CREATE")
                    .build();

            List<FhirResourceHistory> historyRecords = List.of(history1);

            when(mongoTemplate.bulkOps(eq(BulkOperations.BulkMode.UNORDERED),
                    eq(FhirResourceHistory.class)))
                    .thenReturn(bulkOperations);
            when(bulkOperations.execute()).thenReturn(bulkWriteResult);
            when(bulkWriteResult.getInsertedCount()).thenReturn(1);

            CompletableFuture<BulkWriteResult> future = batchService.bulkInsertHistoryAsync(historyRecords);

            assertNotNull(future);
            BulkWriteResult result = future.join();
            assertNotNull(result);
        }

        @Test
        @DisplayName("Should return completed future for empty list")
        void shouldReturnCompletedFutureForEmptyList() {
            CompletableFuture<BulkWriteResult> future = batchService.bulkInsertHistoryAsync(Collections.emptyList());

            assertNotNull(future);
            assertTrue(future.isDone());
            assertNull(future.join());
        }

        @Test
        @DisplayName("Should return completed future for null list")
        void shouldReturnCompletedFutureForNullList() {
            CompletableFuture<BulkWriteResult> future = batchService.bulkInsertHistoryAsync(null);

            assertNotNull(future);
            assertTrue(future.isDone());
            assertNull(future.join());
        }
    }

    @Nested
    @DisplayName("Process Large Transaction Tests")
    class ProcessLargeTransactionTests {

        @Test
        @DisplayName("Should process transaction bundle with POST entries")
        void shouldProcessTransactionBundleWithPostEntries() {
            Bundle transactionBundle = new Bundle();
            transactionBundle.setType(Bundle.BundleType.TRANSACTION);

            Patient patient = new Patient();
            patient.addName().setFamily("Test");

            Bundle.BundleEntryComponent entry = transactionBundle.addEntry();
            entry.setResource(patient);
            entry.setFullUrl("urn:uuid:123");
            entry.getRequest()
                    .setMethod(Bundle.HTTPVerb.POST)
                    .setUrl("Patient");

            when(mongoTemplate.bulkOps(eq(BulkOperations.BulkMode.UNORDERED),
                    eq(FhirResourceDocument.class), anyString()))
                    .thenReturn(bulkOperations);
            when(bulkOperations.execute()).thenReturn(bulkWriteResult);
            when(bulkWriteResult.getInsertedCount()).thenReturn(1);
            when(mongoTemplate.bulkOps(eq(BulkOperations.BulkMode.UNORDERED),
                    eq(FhirResourceHistory.class)))
                    .thenReturn(bulkOperations);

            Bundle responseBundle = batchService.processLargeTransaction(transactionBundle);

            assertNotNull(responseBundle);
            assertEquals(Bundle.BundleType.TRANSACTIONRESPONSE, responseBundle.getType());
            assertFalse(responseBundle.getEntry().isEmpty());
        }

        @Test
        @DisplayName("Should process batch bundle")
        void shouldProcessBatchBundle() {
            Bundle batchBundle = new Bundle();
            batchBundle.setType(Bundle.BundleType.BATCH);

            Patient patient = new Patient();
            patient.addName().setFamily("Test");

            Bundle.BundleEntryComponent entry = batchBundle.addEntry();
            entry.setResource(patient);
            entry.getRequest()
                    .setMethod(Bundle.HTTPVerb.POST)
                    .setUrl("Patient");

            when(mongoTemplate.bulkOps(eq(BulkOperations.BulkMode.UNORDERED),
                    eq(FhirResourceDocument.class), anyString()))
                    .thenReturn(bulkOperations);
            when(bulkOperations.execute()).thenReturn(bulkWriteResult);
            when(bulkWriteResult.getInsertedCount()).thenReturn(1);
            when(mongoTemplate.bulkOps(eq(BulkOperations.BulkMode.UNORDERED),
                    eq(FhirResourceHistory.class)))
                    .thenReturn(bulkOperations);

            Bundle responseBundle = batchService.processLargeTransaction(batchBundle);

            assertNotNull(responseBundle);
            assertEquals(Bundle.BundleType.BATCHRESPONSE, responseBundle.getType());
        }

        @Test
        @DisplayName("Should handle empty transaction bundle")
        void shouldHandleEmptyTransactionBundle() {
            Bundle transactionBundle = new Bundle();
            transactionBundle.setType(Bundle.BundleType.TRANSACTION);

            Bundle responseBundle = batchService.processLargeTransaction(transactionBundle);

            assertNotNull(responseBundle);
            assertEquals(Bundle.BundleType.TRANSACTIONRESPONSE, responseBundle.getType());
            assertTrue(responseBundle.getEntry().isEmpty());
        }

        @Test
        @DisplayName("Should process DELETE entries")
        void shouldProcessDeleteEntries() {
            Bundle transactionBundle = new Bundle();
            transactionBundle.setType(Bundle.BundleType.TRANSACTION);

            Bundle.BundleEntryComponent entry = transactionBundle.addEntry();
            entry.getRequest()
                    .setMethod(Bundle.HTTPVerb.DELETE)
                    .setUrl("Patient/p-123");

            Bundle responseBundle = batchService.processLargeTransaction(transactionBundle);

            assertNotNull(responseBundle);
            assertFalse(responseBundle.getEntry().isEmpty());
            assertEquals("204 No Content", responseBundle.getEntry().get(0).getResponse().getStatus());
        }
    }

    @Nested
    @DisplayName("Get Recommended Batch Size Tests")
    class GetRecommendedBatchSizeTests {

        @Test
        @DisplayName("Should return smaller batch size for DiagnosticReport")
        void shouldReturnSmallerBatchSizeForDiagnosticReport() {
            int batchSize = batchService.getRecommendedBatchSize("DiagnosticReport");
            assertEquals(100, batchSize);
        }

        @Test
        @DisplayName("Should return smaller batch size for Bundle")
        void shouldReturnSmallerBatchSizeForBundle() {
            int batchSize = batchService.getRecommendedBatchSize("Bundle");
            assertEquals(100, batchSize);
        }

        @Test
        @DisplayName("Should return smaller batch size for Composition")
        void shouldReturnSmallerBatchSizeForComposition() {
            int batchSize = batchService.getRecommendedBatchSize("Composition");
            assertEquals(100, batchSize);
        }

        @Test
        @DisplayName("Should return smallest batch size for DocumentReference")
        void shouldReturnSmallestBatchSizeForDocumentReference() {
            int batchSize = batchService.getRecommendedBatchSize("DocumentReference");
            assertEquals(50, batchSize);
        }

        @Test
        @DisplayName("Should return smallest batch size for Binary")
        void shouldReturnSmallestBatchSizeForBinary() {
            int batchSize = batchService.getRecommendedBatchSize("Binary");
            assertEquals(50, batchSize);
        }

        @Test
        @DisplayName("Should return default batch size for Patient")
        void shouldReturnDefaultBatchSizeForPatient() {
            int batchSize = batchService.getRecommendedBatchSize("Patient");
            assertEquals(1000, batchSize);
        }

        @Test
        @DisplayName("Should return default batch size for Observation")
        void shouldReturnDefaultBatchSizeForObservation() {
            int batchSize = batchService.getRecommendedBatchSize("Observation");
            assertEquals(1000, batchSize);
        }
    }

    @Nested
    @DisplayName("Process Large Transaction Async Tests")
    class ProcessLargeTransactionAsyncTests {

        @Test
        @DisplayName("Should process transaction asynchronously")
        void shouldProcessTransactionAsynchronously() {
            Bundle transactionBundle = new Bundle();
            transactionBundle.setType(Bundle.BundleType.TRANSACTION);

            CompletableFuture<Bundle> future = batchService.processLargeTransactionAsync(transactionBundle);

            assertNotNull(future);
            Bundle result = future.join();
            assertNotNull(result);
            assertEquals(Bundle.BundleType.TRANSACTIONRESPONSE, result.getType());
        }
    }
}
