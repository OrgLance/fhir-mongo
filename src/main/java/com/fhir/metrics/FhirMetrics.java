package com.fhir.metrics;

import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics collection for FHIR server monitoring.
 * Exposes Prometheus-compatible metrics for observability.
 */
@Component
public class FhirMetrics {

    private final MeterRegistry registry;

    // Counters for resource operations
    private final Counter resourceCreates;
    private final Counter resourceReads;
    private final Counter resourceUpdates;
    private final Counter resourceDeletes;
    private final Counter searchOperations;
    private final Counter cacheHits;
    private final Counter cacheMisses;

    // Timers for latency tracking
    private final Timer createLatency;
    private final Timer readLatency;
    private final Timer updateLatency;
    private final Timer searchLatency;
    private final Timer transactionLatency;

    // Gauges for current state
    private final AtomicLong activeConnections = new AtomicLong(0);
    private final AtomicLong pendingOperations = new AtomicLong(0);

    public FhirMetrics(MeterRegistry registry) {
        this.registry = registry;

        // Initialize counters
        this.resourceCreates = Counter.builder("fhir.resource.operations")
                .tag("operation", "create")
                .description("Number of resource create operations")
                .register(registry);

        this.resourceReads = Counter.builder("fhir.resource.operations")
                .tag("operation", "read")
                .description("Number of resource read operations")
                .register(registry);

        this.resourceUpdates = Counter.builder("fhir.resource.operations")
                .tag("operation", "update")
                .description("Number of resource update operations")
                .register(registry);

        this.resourceDeletes = Counter.builder("fhir.resource.operations")
                .tag("operation", "delete")
                .description("Number of resource delete operations")
                .register(registry);

        this.searchOperations = Counter.builder("fhir.search.operations")
                .description("Number of search operations")
                .register(registry);

        this.cacheHits = Counter.builder("fhir.cache.operations")
                .tag("result", "hit")
                .description("Number of cache hits")
                .register(registry);

        this.cacheMisses = Counter.builder("fhir.cache.operations")
                .tag("result", "miss")
                .description("Number of cache misses")
                .register(registry);

        // Initialize timers
        this.createLatency = Timer.builder("fhir.resource.latency")
                .tag("operation", "create")
                .description("Resource create latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        this.readLatency = Timer.builder("fhir.resource.latency")
                .tag("operation", "read")
                .description("Resource read latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        this.updateLatency = Timer.builder("fhir.resource.latency")
                .tag("operation", "update")
                .description("Resource update latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        this.searchLatency = Timer.builder("fhir.search.latency")
                .description("Search operation latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        this.transactionLatency = Timer.builder("fhir.transaction.latency")
                .description("Transaction/batch operation latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        // Initialize gauges
        Gauge.builder("fhir.connections.active", activeConnections, AtomicLong::get)
                .description("Number of active connections")
                .register(registry);

        Gauge.builder("fhir.operations.pending", pendingOperations, AtomicLong::get)
                .description("Number of pending operations")
                .register(registry);
    }

    // ========== Counter Methods ==========

    public void recordCreate(String resourceType) {
        resourceCreates.increment();
        Counter.builder("fhir.resource.creates")
                .tag("type", resourceType)
                .register(registry)
                .increment();
    }

    public void recordRead(String resourceType) {
        resourceReads.increment();
    }

    public void recordUpdate(String resourceType) {
        resourceUpdates.increment();
    }

    public void recordDelete(String resourceType) {
        resourceDeletes.increment();
    }

    public void recordSearch(String resourceType, int resultCount) {
        searchOperations.increment();
        Counter.builder("fhir.search.results")
                .tag("type", resourceType)
                .register(registry)
                .increment(resultCount);
    }

    public void recordCacheHit() {
        cacheHits.increment();
    }

    public void recordCacheMiss() {
        cacheMisses.increment();
    }

    // ========== Timer Methods ==========

    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    public void recordCreateLatency(Timer.Sample sample) {
        sample.stop(createLatency);
    }

    public void recordReadLatency(Timer.Sample sample) {
        sample.stop(readLatency);
    }

    public void recordUpdateLatency(Timer.Sample sample) {
        sample.stop(updateLatency);
    }

    public void recordSearchLatency(Timer.Sample sample) {
        sample.stop(searchLatency);
    }

    public void recordTransactionLatency(Timer.Sample sample) {
        sample.stop(transactionLatency);
    }

    public void recordLatency(String operation, long durationMs) {
        Timer.builder("fhir.operation.duration")
                .tag("operation", operation)
                .register(registry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    // ========== Gauge Methods ==========

    public void incrementActiveConnections() {
        activeConnections.incrementAndGet();
    }

    public void decrementActiveConnections() {
        activeConnections.decrementAndGet();
    }

    public void incrementPendingOperations() {
        pendingOperations.incrementAndGet();
    }

    public void decrementPendingOperations() {
        pendingOperations.decrementAndGet();
    }

    // ========== Resource-Specific Counters ==========

    public void recordBulkOperation(String operation, int count) {
        Counter.builder("fhir.bulk.operations")
                .tag("operation", operation)
                .register(registry)
                .increment(count);
    }

    public void recordError(String operation, String errorType) {
        Counter.builder("fhir.errors")
                .tag("operation", operation)
                .tag("type", errorType)
                .register(registry)
                .increment();
    }

    public void recordValidationResult(boolean valid) {
        Counter.builder("fhir.validation.results")
                .tag("valid", String.valueOf(valid))
                .register(registry)
                .increment();
    }
}
