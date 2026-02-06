package com.fhir.service;

import com.fhir.model.AuditLog;
import com.fhir.model.AuditLog.AuditAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuditLogService Tests")
class AuditLogServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(auditLogService, "auditEnabled", true);
        ReflectionTestUtils.setField(auditLogService, "retentionDays", 90);
        ReflectionTestUtils.setField(auditLogService, "granularity", "SECONDS");
    }

    @Nested
    @DisplayName("Collection Name Tests")
    class CollectionNameTests {

        @Test
        @DisplayName("Should generate correct collection name for Patient")
        void shouldGenerateCorrectCollectionNameForPatient() {
            String collectionName = auditLogService.getAuditCollectionName("Patient");
            assertEquals("audit_patient", collectionName);
        }

        @Test
        @DisplayName("Should generate lowercase collection name")
        void shouldGenerateLowercaseCollectionName() {
            String collectionName = auditLogService.getAuditCollectionName("OBSERVATION");
            assertEquals("audit_observation", collectionName);
        }

        @Test
        @DisplayName("Should handle mixed case resource type")
        void shouldHandleMixedCaseResourceType() {
            String collectionName = auditLogService.getAuditCollectionName("MedicationRequest");
            assertEquals("audit_medicationrequest", collectionName);
        }
    }

    @Nested
    @DisplayName("Create Audit Log Tests")
    class CreateAuditLogTests {

        @Test
        @DisplayName("Should create basic audit log")
        void shouldCreateBasicAuditLog() {
            AuditLog log = auditLogService.createAuditLog("Patient", "p-123", AuditAction.CREATE);

            assertNotNull(log);
            assertEquals("Patient", log.getResourceType());
            assertEquals("p-123", log.getResourceId());
            assertEquals(AuditAction.CREATE, log.getAction());
            assertNotNull(log.getTimestamp());
            assertNotNull(log.getMetadata());
        }

        @Test
        @DisplayName("Should create audit log with full details")
        void shouldCreateAuditLogWithFullDetails() {
            AuditLog log = auditLogService.createAuditLog(
                    "Patient", "p-123", AuditAction.UPDATE,
                    2L, "user1", "192.168.1.1",
                    "PUT", "/Patient/p-123", 200, 50L);

            assertNotNull(log);
            assertEquals("Patient", log.getResourceType());
            assertEquals("p-123", log.getResourceId());
            assertEquals(AuditAction.UPDATE, log.getAction());
            assertEquals(2L, log.getVersionId());
            assertEquals("user1", log.getActor());
            assertEquals("192.168.1.1", log.getSource());
            assertEquals("PUT", log.getHttpMethod());
            assertEquals("/Patient/p-123", log.getRequestUri());
            assertEquals(200, log.getStatusCode());
            assertEquals(50L, log.getDurationMs());
        }
    }

    @Nested
    @DisplayName("Log Method Tests")
    class LogMethodTests {

        @Test
        @DisplayName("Should not log when audit is disabled")
        void shouldNotLogWhenAuditDisabled() {
            ReflectionTestUtils.setField(auditLogService, "auditEnabled", false);

            AuditLog log = auditLogService.createAuditLog("Patient", "p-123", AuditAction.READ);
            auditLogService.log(log);

            verify(mongoTemplate, never()).save(any(AuditLog.class), anyString());
        }

        @Test
        @DisplayName("Should not log null audit log")
        void shouldNotLogNullAuditLog() {
            auditLogService.log(null);

            verify(mongoTemplate, never()).save(any(AuditLog.class), anyString());
        }

        @Test
        @DisplayName("Should not log audit without resource type")
        void shouldNotLogAuditWithoutResourceType() {
            AuditLog log = AuditLog.builder()
                    .resourceId("p-123")
                    .action(AuditAction.READ)
                    .build();

            auditLogService.log(log);

            verify(mongoTemplate, never()).save(any(AuditLog.class), anyString());
        }
    }

    @Nested
    @DisplayName("Async Log Tests")
    class AsyncLogTests {

        @Test
        @DisplayName("Should return completed future when audit disabled")
        void shouldReturnCompletedFutureWhenAuditDisabled() {
            ReflectionTestUtils.setField(auditLogService, "auditEnabled", false);

            AuditLog log = auditLogService.createAuditLog("Patient", "p-123", AuditAction.READ);
            CompletableFuture<Void> future = auditLogService.logAsync(log);

            assertNotNull(future);
            assertTrue(future.isDone());
        }
    }

    @Nested
    @DisplayName("Audit Action Tests")
    class AuditActionTests {

        @Test
        @DisplayName("Should have all CRUD actions")
        void shouldHaveAllCrudActions() {
            assertNotNull(AuditAction.CREATE);
            assertNotNull(AuditAction.READ);
            assertNotNull(AuditAction.UPDATE);
            assertNotNull(AuditAction.DELETE);
        }

        @Test
        @DisplayName("Should have search and history actions")
        void shouldHaveSearchAndHistoryActions() {
            assertNotNull(AuditAction.SEARCH);
            assertNotNull(AuditAction.HISTORY);
            assertNotNull(AuditAction.VREAD);
        }

        @Test
        @DisplayName("Should have transaction and batch actions")
        void shouldHaveTransactionAndBatchActions() {
            assertNotNull(AuditAction.TRANSACTION);
            assertNotNull(AuditAction.BATCH);
            assertNotNull(AuditAction.VALIDATE);
        }
    }

    @Nested
    @DisplayName("Audit Log Model Tests")
    class AuditLogModelTests {

        @Test
        @DisplayName("Should build audit log with builder")
        void shouldBuildAuditLogWithBuilder() {
            Instant now = Instant.now();
            AuditLog log = AuditLog.builder()
                    .timestamp(now)
                    .resourceType("Observation")
                    .resourceId("obs-123")
                    .action(AuditAction.CREATE)
                    .versionId(1L)
                    .actor("system")
                    .source("localhost")
                    .httpMethod("POST")
                    .requestUri("/Observation")
                    .statusCode(201)
                    .durationMs(100L)
                    .build();

            assertEquals(now, log.getTimestamp());
            assertEquals("Observation", log.getResourceType());
            assertEquals("obs-123", log.getResourceId());
            assertEquals(AuditAction.CREATE, log.getAction());
            assertEquals(1L, log.getVersionId());
            assertEquals("system", log.getActor());
            assertEquals("localhost", log.getSource());
            assertEquals("POST", log.getHttpMethod());
            assertEquals("/Observation", log.getRequestUri());
            assertEquals(201, log.getStatusCode());
            assertEquals(100L, log.getDurationMs());
        }

        @Test
        @DisplayName("Should build audit metadata")
        void shouldBuildAuditMetadata() {
            AuditLog.AuditMetadata metadata = AuditLog.AuditMetadata.builder()
                    .resourceType("Patient")
                    .actor("admin")
                    .source("api-gateway")
                    .build();

            assertEquals("Patient", metadata.getResourceType());
            assertEquals("admin", metadata.getActor());
            assertEquals("api-gateway", metadata.getSource());
        }
    }
}
