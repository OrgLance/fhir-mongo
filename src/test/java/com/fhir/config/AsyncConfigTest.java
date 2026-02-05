package com.fhir.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AsyncConfig Tests")
class AsyncConfigTest {

    private AsyncConfig config;

    @BeforeEach
    void setUp() {
        config = new AsyncConfig();
        ReflectionTestUtils.setField(config, "corePoolSize", 10);
        ReflectionTestUtils.setField(config, "maxPoolSize", 50);
        ReflectionTestUtils.setField(config, "queueCapacity", 500);
    }

    @Test
    @DisplayName("Should create FHIR task executor")
    void shouldCreateFhirTaskExecutor() {
        Executor executor = config.fhirTaskExecutor();

        assertNotNull(executor);
        assertTrue(executor instanceof ThreadPoolTaskExecutor);

        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
        assertEquals(10, taskExecutor.getCorePoolSize());
        assertEquals(50, taskExecutor.getMaxPoolSize());
    }

    @Test
    @DisplayName("Should create history task executor")
    void shouldCreateHistoryTaskExecutor() {
        Executor executor = config.historyTaskExecutor();

        assertNotNull(executor);
        assertTrue(executor instanceof ThreadPoolTaskExecutor);

        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
        assertEquals(5, taskExecutor.getCorePoolSize());
        assertEquals(20, taskExecutor.getMaxPoolSize());
    }

    @Test
    @DisplayName("Should create bulk task executor")
    void shouldCreateBulkTaskExecutor() {
        Executor executor = config.bulkTaskExecutor();

        assertNotNull(executor);
        assertTrue(executor instanceof ThreadPoolTaskExecutor);

        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
        assertEquals(4, taskExecutor.getCorePoolSize());
        assertEquals(16, taskExecutor.getMaxPoolSize());
    }

    @Test
    @DisplayName("Should get async executor")
    void shouldGetAsyncExecutor() {
        Executor executor = config.getAsyncExecutor();

        assertNotNull(executor);
    }

    @Test
    @DisplayName("Should get async uncaught exception handler")
    void shouldGetAsyncUncaughtExceptionHandler() {
        AsyncUncaughtExceptionHandler handler = config.getAsyncUncaughtExceptionHandler();

        assertNotNull(handler);
    }

    @Test
    @DisplayName("FHIR executor should have correct thread name prefix")
    void fhirExecutorShouldHaveCorrectThreadNamePrefix() {
        Executor executor = config.fhirTaskExecutor();
        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;

        assertEquals("FHIR-Async-", taskExecutor.getThreadNamePrefix());
    }

    @Test
    @DisplayName("History executor should have correct thread name prefix")
    void historyExecutorShouldHaveCorrectThreadNamePrefix() {
        Executor executor = config.historyTaskExecutor();
        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;

        assertEquals("FHIR-History-", taskExecutor.getThreadNamePrefix());
    }

    @Test
    @DisplayName("Bulk executor should have correct thread name prefix")
    void bulkExecutorShouldHaveCorrectThreadNamePrefix() {
        Executor executor = config.bulkTaskExecutor();
        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;

        assertEquals("FHIR-Bulk-", taskExecutor.getThreadNamePrefix());
    }

    @Test
    @DisplayName("Exception handler should handle uncaught exceptions")
    void exceptionHandlerShouldHandleUncaughtExceptions() throws NoSuchMethodException {
        AsyncUncaughtExceptionHandler handler = config.getAsyncUncaughtExceptionHandler();

        // Should not throw - just logs the exception
        assertDoesNotThrow(() ->
            handler.handleUncaughtException(
                new RuntimeException("Test error"),
                Object.class.getMethod("toString"),
                "param1"
            )
        );
    }
}
