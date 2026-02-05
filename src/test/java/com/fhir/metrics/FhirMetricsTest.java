package com.fhir.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FhirMetrics Tests")
class FhirMetricsTest {

    private MeterRegistry registry;
    private FhirMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new FhirMetrics(registry);
    }

    @Nested
    @DisplayName("Counter Tests")
    class CounterTests {

        @Test
        @DisplayName("Should record create operations")
        void shouldRecordCreateOperations() {
            metrics.recordCreate("Patient");
            metrics.recordCreate("Observation");

            assertNotNull(registry.get("fhir.resource.operations").tag("operation", "create").counter());
            assertTrue(registry.get("fhir.resource.operations").tag("operation", "create").counter().count() >= 2);
        }

        @Test
        @DisplayName("Should record read operations")
        void shouldRecordReadOperations() {
            metrics.recordRead("Patient");
            metrics.recordRead("Patient");
            metrics.recordRead("Observation");

            assertEquals(3, registry.get("fhir.resource.operations").tag("operation", "read").counter().count());
        }

        @Test
        @DisplayName("Should record update operations")
        void shouldRecordUpdateOperations() {
            metrics.recordUpdate("Patient");

            assertEquals(1, registry.get("fhir.resource.operations").tag("operation", "update").counter().count());
        }

        @Test
        @DisplayName("Should record delete operations")
        void shouldRecordDeleteOperations() {
            metrics.recordDelete("Patient");
            metrics.recordDelete("Observation");

            assertEquals(2, registry.get("fhir.resource.operations").tag("operation", "delete").counter().count());
        }

        @Test
        @DisplayName("Should record search operations")
        void shouldRecordSearchOperations() {
            metrics.recordSearch("Patient", 10);
            metrics.recordSearch("Observation", 5);

            assertEquals(2, registry.get("fhir.search.operations").counter().count());
        }

        @Test
        @DisplayName("Should record cache hits")
        void shouldRecordCacheHits() {
            metrics.recordCacheHit();
            metrics.recordCacheHit();

            assertEquals(2, registry.get("fhir.cache.operations").tag("result", "hit").counter().count());
        }

        @Test
        @DisplayName("Should record cache misses")
        void shouldRecordCacheMisses() {
            metrics.recordCacheMiss();

            assertEquals(1, registry.get("fhir.cache.operations").tag("result", "miss").counter().count());
        }

        @Test
        @DisplayName("Should record bulk operations")
        void shouldRecordBulkOperations() {
            metrics.recordBulkOperation("insert", 100);

            assertTrue(registry.get("fhir.bulk.operations").tag("operation", "insert").counter().count() >= 100);
        }

        @Test
        @DisplayName("Should record errors")
        void shouldRecordErrors() {
            metrics.recordError("create", "validation");
            metrics.recordError("create", "database");

            assertEquals(1, registry.get("fhir.errors").tag("operation", "create").tag("type", "validation").counter().count());
            assertEquals(1, registry.get("fhir.errors").tag("operation", "create").tag("type", "database").counter().count());
        }

        @Test
        @DisplayName("Should record validation results")
        void shouldRecordValidationResults() {
            metrics.recordValidationResult(true);
            metrics.recordValidationResult(false);
            metrics.recordValidationResult(true);

            assertEquals(2, registry.get("fhir.validation.results").tag("valid", "true").counter().count());
            assertEquals(1, registry.get("fhir.validation.results").tag("valid", "false").counter().count());
        }
    }

    @Nested
    @DisplayName("Timer Tests")
    class TimerTests {

        @Test
        @DisplayName("Should start timer")
        void shouldStartTimer() {
            Timer.Sample sample = metrics.startTimer();
            assertNotNull(sample);
        }

        @Test
        @DisplayName("Should record create latency")
        void shouldRecordCreateLatency() {
            Timer.Sample sample = metrics.startTimer();
            metrics.recordCreateLatency(sample);

            assertNotNull(registry.get("fhir.resource.latency").tag("operation", "create").timer());
        }

        @Test
        @DisplayName("Should record read latency")
        void shouldRecordReadLatency() {
            Timer.Sample sample = metrics.startTimer();
            metrics.recordReadLatency(sample);

            assertNotNull(registry.get("fhir.resource.latency").tag("operation", "read").timer());
        }

        @Test
        @DisplayName("Should record update latency")
        void shouldRecordUpdateLatency() {
            Timer.Sample sample = metrics.startTimer();
            metrics.recordUpdateLatency(sample);

            assertNotNull(registry.get("fhir.resource.latency").tag("operation", "update").timer());
        }

        @Test
        @DisplayName("Should record search latency")
        void shouldRecordSearchLatency() {
            Timer.Sample sample = metrics.startTimer();
            metrics.recordSearchLatency(sample);

            assertNotNull(registry.get("fhir.search.latency").timer());
        }

        @Test
        @DisplayName("Should record transaction latency")
        void shouldRecordTransactionLatency() {
            Timer.Sample sample = metrics.startTimer();
            metrics.recordTransactionLatency(sample);

            assertNotNull(registry.get("fhir.transaction.latency").timer());
        }

        @Test
        @DisplayName("Should record generic latency")
        void shouldRecordGenericLatency() {
            metrics.recordLatency("customOperation", 100);

            assertNotNull(registry.get("fhir.operation.duration").tag("operation", "customOperation").timer());
        }
    }

    @Nested
    @DisplayName("Gauge Tests")
    class GaugeTests {

        @Test
        @DisplayName("Should increment active connections")
        void shouldIncrementActiveConnections() {
            metrics.incrementActiveConnections();
            metrics.incrementActiveConnections();

            assertEquals(2, registry.get("fhir.connections.active").gauge().value());
        }

        @Test
        @DisplayName("Should decrement active connections")
        void shouldDecrementActiveConnections() {
            metrics.incrementActiveConnections();
            metrics.incrementActiveConnections();
            metrics.decrementActiveConnections();

            assertEquals(1, registry.get("fhir.connections.active").gauge().value());
        }

        @Test
        @DisplayName("Should increment pending operations")
        void shouldIncrementPendingOperations() {
            metrics.incrementPendingOperations();

            assertEquals(1, registry.get("fhir.operations.pending").gauge().value());
        }

        @Test
        @DisplayName("Should decrement pending operations")
        void shouldDecrementPendingOperations() {
            metrics.incrementPendingOperations();
            metrics.incrementPendingOperations();
            metrics.decrementPendingOperations();

            assertEquals(1, registry.get("fhir.operations.pending").gauge().value());
        }
    }
}
