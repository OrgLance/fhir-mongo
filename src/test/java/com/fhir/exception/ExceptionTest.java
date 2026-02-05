package com.fhir.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FHIR Exception Tests")
class ExceptionTest {

    @Nested
    @DisplayName("FhirResourceNotFoundException Tests")
    class FhirResourceNotFoundExceptionTests {

        @Test
        @DisplayName("Should create exception with resource type and ID")
        void shouldCreateExceptionWithResourceTypeAndId() {
            FhirResourceNotFoundException exception = new FhirResourceNotFoundException("Patient", "p-123");

            assertEquals("Patient", exception.getResourceType());
            assertEquals("p-123", exception.getResourceId());
            assertEquals("Resource Patient/p-123 not found", exception.getMessage());
        }

        @Test
        @DisplayName("Should handle different resource types")
        void shouldHandleDifferentResourceTypes() {
            FhirResourceNotFoundException patientException = new FhirResourceNotFoundException("Patient", "p-123");
            FhirResourceNotFoundException observationException = new FhirResourceNotFoundException("Observation", "o-456");
            FhirResourceNotFoundException encounterException = new FhirResourceNotFoundException("Encounter", "e-789");

            assertEquals("Patient", patientException.getResourceType());
            assertEquals("Observation", observationException.getResourceType());
            assertEquals("Encounter", encounterException.getResourceType());
        }

        @Test
        @DisplayName("Should be a RuntimeException")
        void shouldBeARuntimeException() {
            FhirResourceNotFoundException exception = new FhirResourceNotFoundException("Patient", "p-123");

            assertTrue(exception instanceof RuntimeException);
        }
    }

    @Nested
    @DisplayName("FhirValidationException Tests")
    class FhirValidationExceptionTests {

        @Test
        @DisplayName("Should create exception with single message")
        void shouldCreateExceptionWithSingleMessage() {
            FhirValidationException exception = new FhirValidationException("Invalid resource format");

            assertEquals("Invalid resource format", exception.getMessage());
            assertEquals(1, exception.getValidationErrors().size());
            assertEquals("Invalid resource format", exception.getValidationErrors().get(0));
        }

        @Test
        @DisplayName("Should create exception with multiple validation errors")
        void shouldCreateExceptionWithMultipleValidationErrors() {
            List<String> errors = List.of(
                    "Missing required field: name",
                    "Invalid date format",
                    "Unknown extension"
            );

            FhirValidationException exception = new FhirValidationException(errors);

            assertEquals(3, exception.getValidationErrors().size());
            assertTrue(exception.getMessage().contains("Missing required field: name"));
            assertTrue(exception.getMessage().contains("Invalid date format"));
            assertTrue(exception.getMessage().contains("Unknown extension"));
        }

        @Test
        @DisplayName("Should include all errors in message")
        void shouldIncludeAllErrorsInMessage() {
            List<String> errors = List.of("Error 1", "Error 2");
            FhirValidationException exception = new FhirValidationException(errors);

            assertTrue(exception.getMessage().startsWith("FHIR validation failed:"));
            assertTrue(exception.getMessage().contains("Error 1"));
            assertTrue(exception.getMessage().contains("Error 2"));
        }

        @Test
        @DisplayName("Should be a RuntimeException")
        void shouldBeARuntimeException() {
            FhirValidationException exception = new FhirValidationException("Test error");

            assertTrue(exception instanceof RuntimeException);
        }
    }

    @Nested
    @DisplayName("FhirOperationException Tests")
    class FhirOperationExceptionTests {

        @Test
        @DisplayName("Should create operation exception with operation type and message")
        void shouldCreateOperationExceptionWithOperationTypeAndMessage() {
            FhirOperationException exception = new FhirOperationException("CREATE", "Database connection failed");

            assertEquals("CREATE", exception.getOperationType());
            assertEquals("Database connection failed", exception.getMessage());
            assertTrue(exception instanceof RuntimeException);
        }

        @Test
        @DisplayName("Should create operation exception with cause")
        void shouldCreateOperationExceptionWithCause() {
            RuntimeException cause = new RuntimeException("Root cause");
            FhirOperationException exception = new FhirOperationException("UPDATE", "Operation failed", cause);

            assertEquals("UPDATE", exception.getOperationType());
            assertEquals("Operation failed", exception.getMessage());
            assertEquals(cause, exception.getCause());
        }

        @Test
        @DisplayName("Should handle different operation types")
        void shouldHandleDifferentOperationTypes() {
            FhirOperationException createException = new FhirOperationException("CREATE", "Create failed");
            FhirOperationException readException = new FhirOperationException("READ", "Read failed");
            FhirOperationException updateException = new FhirOperationException("UPDATE", "Update failed");
            FhirOperationException deleteException = new FhirOperationException("DELETE", "Delete failed");

            assertEquals("CREATE", createException.getOperationType());
            assertEquals("READ", readException.getOperationType());
            assertEquals("UPDATE", updateException.getOperationType());
            assertEquals("DELETE", deleteException.getOperationType());
        }
    }
}
