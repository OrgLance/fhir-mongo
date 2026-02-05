package com.fhir.model;

import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FhirResourceDocument Tests")
class FhirResourceDocumentTest {

    @Test
    @DisplayName("Should create document using builder")
    void shouldCreateDocumentUsingBuilder() {
        Instant now = Instant.now();
        Document resourceData = Document.parse("{\"resourceType\":\"Patient\"}");

        FhirResourceDocument doc = FhirResourceDocument.builder()
                .id("doc-1")
                .resourceType("Patient")
                .resourceId("p-123")
                .resourceJson("{\"resourceType\":\"Patient\",\"id\":\"p-123\"}")
                .resourceData(resourceData)
                .versionId(1L)
                .lastUpdated(now)
                .createdAt(now)
                .active(true)
                .deleted(false)
                .isCompressed(false)
                .build();

        assertEquals("doc-1", doc.getId());
        assertEquals("Patient", doc.getResourceType());
        assertEquals("p-123", doc.getResourceId());
        assertNotNull(doc.getResourceJson());
        assertNotNull(doc.getResourceData());
        assertEquals(1L, doc.getVersionId());
        assertEquals(now, doc.getLastUpdated());
        assertEquals(now, doc.getCreatedAt());
        assertTrue(doc.getActive());
        assertFalse(doc.getDeleted());
        assertFalse(doc.getIsCompressed());
    }

    @Test
    @DisplayName("Should handle compressed data")
    void shouldHandleCompressedData() {
        byte[] compressedData = new byte[]{1, 2, 3, 4, 5};

        FhirResourceDocument doc = FhirResourceDocument.builder()
                .resourceType("Patient")
                .resourceId("p-123")
                .compressedJson(compressedData)
                .isCompressed(true)
                .build();

        assertTrue(doc.getIsCompressed());
        assertArrayEquals(compressedData, doc.getCompressedJson());
    }

    @Test
    @DisplayName("Should support no-args constructor")
    void shouldSupportNoArgsConstructor() {
        FhirResourceDocument doc = new FhirResourceDocument();

        assertNull(doc.getId());
        assertNull(doc.getResourceType());
        assertNull(doc.getResourceId());
    }

    @Test
    @DisplayName("Should support all-args constructor")
    void shouldSupportAllArgsConstructor() {
        Instant now = Instant.now();
        Document resourceData = Document.parse("{\"test\":true}");
        byte[] compressed = new byte[]{1, 2, 3};

        FhirResourceDocument doc = new FhirResourceDocument(
                "id-1",
                "Observation",
                "obs-123",
                "{\"resourceType\":\"Observation\"}",
                resourceData,
                2L,
                now,
                now,
                true,
                false,
                compressed,
                true
        );

        assertEquals("id-1", doc.getId());
        assertEquals("Observation", doc.getResourceType());
        assertEquals("obs-123", doc.getResourceId());
        assertEquals(2L, doc.getVersionId());
        assertTrue(doc.getIsCompressed());
    }

    @Test
    @DisplayName("Should support setters")
    void shouldSupportSetters() {
        FhirResourceDocument doc = new FhirResourceDocument();

        doc.setId("new-id");
        doc.setResourceType("Encounter");
        doc.setResourceId("enc-456");
        doc.setVersionId(5L);
        doc.setDeleted(true);

        assertEquals("new-id", doc.getId());
        assertEquals("Encounter", doc.getResourceType());
        assertEquals("enc-456", doc.getResourceId());
        assertEquals(5L, doc.getVersionId());
        assertTrue(doc.getDeleted());
    }

    @Test
    @DisplayName("Should implement equals and hashCode correctly")
    void shouldImplementEqualsAndHashCodeCorrectly() {
        FhirResourceDocument doc1 = FhirResourceDocument.builder()
                .id("doc-1")
                .resourceType("Patient")
                .resourceId("p-123")
                .build();

        FhirResourceDocument doc2 = FhirResourceDocument.builder()
                .id("doc-1")
                .resourceType("Patient")
                .resourceId("p-123")
                .build();

        FhirResourceDocument doc3 = FhirResourceDocument.builder()
                .id("doc-2")
                .resourceType("Patient")
                .resourceId("p-456")
                .build();

        assertEquals(doc1, doc2);
        assertNotEquals(doc1, doc3);
        assertEquals(doc1.hashCode(), doc2.hashCode());
    }

    @Test
    @DisplayName("Should implement toString")
    void shouldImplementToString() {
        FhirResourceDocument doc = FhirResourceDocument.builder()
                .id("doc-1")
                .resourceType("Patient")
                .resourceId("p-123")
                .build();

        String toString = doc.toString();

        assertNotNull(toString);
        assertTrue(toString.contains("Patient"));
        assertTrue(toString.contains("p-123"));
    }
}
