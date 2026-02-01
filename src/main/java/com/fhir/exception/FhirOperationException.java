package com.fhir.exception;

public class FhirOperationException extends RuntimeException {

    private final String operationType;

    public FhirOperationException(String operationType, String message) {
        super(message);
        this.operationType = operationType;
    }

    public FhirOperationException(String operationType, String message, Throwable cause) {
        super(message, cause);
        this.operationType = operationType;
    }

    public String getOperationType() {
        return operationType;
    }
}
