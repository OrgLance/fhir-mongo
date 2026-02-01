package com.fhir.exception;

public class FhirResourceNotFoundException extends RuntimeException {

    private final String resourceType;
    private final String resourceId;

    public FhirResourceNotFoundException(String resourceType, String resourceId) {
        super(String.format("Resource %s/%s not found", resourceType, resourceId));
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }
}
