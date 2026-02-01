package com.fhir.exception;

import java.util.List;

public class FhirValidationException extends RuntimeException {

    private final List<String> validationErrors;

    public FhirValidationException(String message) {
        super(message);
        this.validationErrors = List.of(message);
    }

    public FhirValidationException(List<String> validationErrors) {
        super("FHIR validation failed: " + String.join(", ", validationErrors));
        this.validationErrors = validationErrors;
    }

    public List<String> getValidationErrors() {
        return validationErrors;
    }
}
