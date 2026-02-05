package com.fhir.exception;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalExceptionHandler Tests")
class GlobalExceptionHandlerTest {

    @Spy
    private FhirContext fhirContext = FhirContext.forR4();

    @InjectMocks
    private GlobalExceptionHandler exceptionHandler;

    @Nested
    @DisplayName("FhirResourceNotFoundException Handler Tests")
    class ResourceNotFoundHandlerTests {

        @Test
        @DisplayName("Should return 404 for resource not found")
        void shouldReturn404ForResourceNotFound() {
            FhirResourceNotFoundException exception = new FhirResourceNotFoundException("Patient", "p-123");

            ResponseEntity<String> response = exceptionHandler.handleResourceNotFound(exception);

            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertNotNull(response.getBody());

            OperationOutcome outcome = fhirContext.newJsonParser()
                    .parseResource(OperationOutcome.class, response.getBody());
            assertEquals(OperationOutcome.IssueSeverity.ERROR, outcome.getIssue().get(0).getSeverity());
            assertEquals(OperationOutcome.IssueType.NOTFOUND, outcome.getIssue().get(0).getCode());
        }

        @Test
        @DisplayName("Should include resource details in diagnostics")
        void shouldIncludeResourceDetailsInDiagnostics() {
            FhirResourceNotFoundException exception = new FhirResourceNotFoundException("Observation", "obs-456");

            ResponseEntity<String> response = exceptionHandler.handleResourceNotFound(exception);

            OperationOutcome outcome = fhirContext.newJsonParser()
                    .parseResource(OperationOutcome.class, response.getBody());
            assertTrue(outcome.getIssue().get(0).getDiagnostics().contains("Observation"));
            assertTrue(outcome.getIssue().get(0).getDiagnostics().contains("obs-456"));
        }
    }

    @Nested
    @DisplayName("FhirValidationException Handler Tests")
    class ValidationExceptionHandlerTests {

        @Test
        @DisplayName("Should return 400 for validation error")
        void shouldReturn400ForValidationError() {
            FhirValidationException exception = new FhirValidationException("Invalid resource format");

            ResponseEntity<String> response = exceptionHandler.handleValidationException(exception);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertNotNull(response.getBody());

            OperationOutcome outcome = fhirContext.newJsonParser()
                    .parseResource(OperationOutcome.class, response.getBody());
            assertEquals(OperationOutcome.IssueSeverity.ERROR, outcome.getIssue().get(0).getSeverity());
            assertEquals(OperationOutcome.IssueType.INVALID, outcome.getIssue().get(0).getCode());
        }

        @Test
        @DisplayName("Should include all validation errors")
        void shouldIncludeAllValidationErrors() {
            List<String> errors = List.of("Error 1", "Error 2", "Error 3");
            FhirValidationException exception = new FhirValidationException(errors);

            ResponseEntity<String> response = exceptionHandler.handleValidationException(exception);

            OperationOutcome outcome = fhirContext.newJsonParser()
                    .parseResource(OperationOutcome.class, response.getBody());
            assertEquals(3, outcome.getIssue().size());
        }
    }

    @Nested
    @DisplayName("FhirOperationException Handler Tests")
    class OperationExceptionHandlerTests {

        @Test
        @DisplayName("Should return 500 for operation exception")
        void shouldReturn500ForOperationException() {
            FhirOperationException exception = new FhirOperationException("CREATE", "Database error");

            ResponseEntity<String> response = exceptionHandler.handleOperationException(exception);

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertNotNull(response.getBody());

            OperationOutcome outcome = fhirContext.newJsonParser()
                    .parseResource(OperationOutcome.class, response.getBody());
            assertEquals(OperationOutcome.IssueSeverity.ERROR, outcome.getIssue().get(0).getSeverity());
            assertEquals(OperationOutcome.IssueType.PROCESSING, outcome.getIssue().get(0).getCode());
        }
    }

    @Nested
    @DisplayName("DataFormatException Handler Tests")
    class DataFormatExceptionHandlerTests {

        @Test
        @DisplayName("Should return 400 for data format error")
        void shouldReturn400ForDataFormatError() {
            DataFormatException exception = new DataFormatException("Invalid JSON");

            ResponseEntity<String> response = exceptionHandler.handleDataFormatException(exception);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertNotNull(response.getBody());

            OperationOutcome outcome = fhirContext.newJsonParser()
                    .parseResource(OperationOutcome.class, response.getBody());
            assertEquals(OperationOutcome.IssueSeverity.ERROR, outcome.getIssue().get(0).getSeverity());
            assertEquals(OperationOutcome.IssueType.STRUCTURE, outcome.getIssue().get(0).getCode());
        }

        @Test
        @DisplayName("Should include original message in diagnostics")
        void shouldIncludeOriginalMessageInDiagnostics() {
            DataFormatException exception = new DataFormatException("Missing required field");

            ResponseEntity<String> response = exceptionHandler.handleDataFormatException(exception);

            OperationOutcome outcome = fhirContext.newJsonParser()
                    .parseResource(OperationOutcome.class, response.getBody());
            assertTrue(outcome.getIssue().get(0).getDiagnostics().contains("Invalid FHIR resource format"));
        }
    }

    @Nested
    @DisplayName("Generic Exception Handler Tests")
    class GenericExceptionHandlerTests {

        @Test
        @DisplayName("Should return 500 for generic exception")
        void shouldReturn500ForGenericException() {
            Exception exception = new RuntimeException("Unexpected error");

            ResponseEntity<String> response = exceptionHandler.handleGenericException(exception);

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertNotNull(response.getBody());

            OperationOutcome outcome = fhirContext.newJsonParser()
                    .parseResource(OperationOutcome.class, response.getBody());
            assertEquals(OperationOutcome.IssueSeverity.FATAL, outcome.getIssue().get(0).getSeverity());
            assertEquals(OperationOutcome.IssueType.EXCEPTION, outcome.getIssue().get(0).getCode());
        }

        @Test
        @DisplayName("Should include message in diagnostics")
        void shouldIncludeMessageInDiagnostics() {
            Exception exception = new RuntimeException("Specific error message");

            ResponseEntity<String> response = exceptionHandler.handleGenericException(exception);

            OperationOutcome outcome = fhirContext.newJsonParser()
                    .parseResource(OperationOutcome.class, response.getBody());
            assertTrue(outcome.getIssue().get(0).getDiagnostics().contains("Specific error message"));
        }
    }
}
