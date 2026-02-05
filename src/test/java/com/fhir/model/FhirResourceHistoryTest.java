package com.fhir.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FhirResourceHistory Tests")
class FhirResourceHistoryTest {

    @Test
    @DisplayName("Should create history using builder")
    void shouldCreateHistoryUsingBuilder() {
        Instant now = Instant.now();

        FhirResourceHistory history = FhirResourceHistory.builder()
                .id("hist-1")
                .resourceType("Patient")
                .resourceId("p-123")
                .versionId(1L)
                .resourceJson("{\"resourceType\":\"Patient\",\"id\":\"p-123\"}")
                .timestamp(now)
                .action("CREATE")
                .isCompressed(false)
                .build();

        assertEquals("hist-1", history.getId());
        assertEquals("Patient", history.getResourceType());
        assertEquals("p-123", history.getResourceId());
        assertEquals(1L, history.getVersionId());
        assertNotNull(history.getResourceJson());
        assertEquals(now, history.getTimestamp());
        assertEquals("CREATE", history.getAction());
        assertFalse(history.getIsCompressed());
    }

    @Test
    @DisplayName("Should handle compressed history data")
    void shouldHandleCompressedHistoryData() {
        byte[] compressedData = new byte[]{10, 20, 30, 40};

        FhirResourceHistory history = FhirResourceHistory.builder()
                .resourceType("Patient")
                .resourceId("p-123")
                .versionId(1L)
                .compressedJson(compressedData)
                .isCompressed(true)
                .build();

        assertTrue(history.getIsCompressed());
        assertArrayEquals(compressedData, history.getCompressedJson());
    }

    @Test
    @DisplayName("Should support different actions")
    void shouldSupportDifferentActions() {
        FhirResourceHistory createHistory = FhirResourceHistory.builder()
                .action("CREATE")
                .build();
        assertEquals("CREATE", createHistory.getAction());

        FhirResourceHistory updateHistory = FhirResourceHistory.builder()
                .action("UPDATE")
                .build();
        assertEquals("UPDATE", updateHistory.getAction());

        FhirResourceHistory deleteHistory = FhirResourceHistory.builder()
                .action("DELETE")
                .build();
        assertEquals("DELETE", deleteHistory.getAction());
    }

    @Test
    @DisplayName("Should support no-args constructor")
    void shouldSupportNoArgsConstructor() {
        FhirResourceHistory history = new FhirResourceHistory();

        assertNull(history.getId());
        assertNull(history.getResourceType());
        assertNull(history.getAction());
    }

    @Test
    @DisplayName("Should support setters")
    void shouldSupportSetters() {
        FhirResourceHistory history = new FhirResourceHistory();
        Instant now = Instant.now();

        history.setId("hist-123");
        history.setResourceType("Observation");
        history.setResourceId("obs-456");
        history.setVersionId(3L);
        history.setTimestamp(now);
        history.setAction("UPDATE");

        assertEquals("hist-123", history.getId());
        assertEquals("Observation", history.getResourceType());
        assertEquals("obs-456", history.getResourceId());
        assertEquals(3L, history.getVersionId());
        assertEquals(now, history.getTimestamp());
        assertEquals("UPDATE", history.getAction());
    }

    @Test
    @DisplayName("Should implement equals and hashCode correctly")
    void shouldImplementEqualsAndHashCodeCorrectly() {
        Instant now = Instant.now();

        FhirResourceHistory history1 = FhirResourceHistory.builder()
                .id("hist-1")
                .resourceType("Patient")
                .resourceId("p-123")
                .versionId(1L)
                .timestamp(now)
                .build();

        FhirResourceHistory history2 = FhirResourceHistory.builder()
                .id("hist-1")
                .resourceType("Patient")
                .resourceId("p-123")
                .versionId(1L)
                .timestamp(now)
                .build();

        FhirResourceHistory history3 = FhirResourceHistory.builder()
                .id("hist-2")
                .resourceType("Patient")
                .resourceId("p-456")
                .versionId(2L)
                .build();

        assertEquals(history1, history2);
        assertNotEquals(history1, history3);
        assertEquals(history1.hashCode(), history2.hashCode());
    }

    @Test
    @DisplayName("Should implement toString")
    void shouldImplementToString() {
        FhirResourceHistory history = FhirResourceHistory.builder()
                .id("hist-1")
                .resourceType("Patient")
                .resourceId("p-123")
                .versionId(1L)
                .action("CREATE")
                .build();

        String toString = history.toString();

        assertNotNull(toString);
        assertTrue(toString.contains("Patient"));
        assertTrue(toString.contains("p-123"));
    }
}
