package com.fhir.exception;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Autowired
    private FhirContext fhirContext;

    @ExceptionHandler(FhirResourceNotFoundException.class)
    public ResponseEntity<String> handleResourceNotFound(FhirResourceNotFoundException ex) {
        logger.warn("Resource not found: {}", ex.getMessage());
        OperationOutcome outcome = createOperationOutcome(
                OperationOutcome.IssueSeverity.ERROR,
                OperationOutcome.IssueType.NOTFOUND,
                ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(outcome));
    }

    @ExceptionHandler(FhirValidationException.class)
    public ResponseEntity<String> handleValidationException(FhirValidationException ex) {
        logger.warn("Validation error: {}", ex.getMessage());
        OperationOutcome outcome = new OperationOutcome();
        for (String error : ex.getValidationErrors()) {
            outcome.addIssue()
                    .setSeverity(OperationOutcome.IssueSeverity.ERROR)
                    .setCode(OperationOutcome.IssueType.INVALID)
                    .setDiagnostics(error);
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(outcome));
    }

    @ExceptionHandler(FhirOperationException.class)
    public ResponseEntity<String> handleOperationException(FhirOperationException ex) {
        logger.error("Operation error: {}", ex.getMessage());
        OperationOutcome outcome = createOperationOutcome(
                OperationOutcome.IssueSeverity.ERROR,
                OperationOutcome.IssueType.PROCESSING,
                ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(outcome));
    }

    @ExceptionHandler(DataFormatException.class)
    public ResponseEntity<String> handleDataFormatException(DataFormatException ex) {
        logger.warn("Data format error: {}", ex.getMessage());
        OperationOutcome outcome = createOperationOutcome(
                OperationOutcome.IssueSeverity.ERROR,
                OperationOutcome.IssueType.STRUCTURE,
                "Invalid FHIR resource format: " + ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(outcome));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGenericException(Exception ex) {
        logger.error("Unexpected error: ", ex);
        OperationOutcome outcome = createOperationOutcome(
                OperationOutcome.IssueSeverity.FATAL,
                OperationOutcome.IssueType.EXCEPTION,
                "An unexpected error occurred: " + ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(outcome));
    }

    private OperationOutcome createOperationOutcome(
            OperationOutcome.IssueSeverity severity,
            OperationOutcome.IssueType type,
            String diagnostics) {
        OperationOutcome outcome = new OperationOutcome();
        outcome.addIssue()
                .setSeverity(severity)
                .setCode(type)
                .setDiagnostics(diagnostics)
                .setDetails(new CodeableConcept().setText(diagnostics));
        return outcome;
    }
}
