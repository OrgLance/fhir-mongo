package com.fhir.controller;

import ca.uhn.fhir.context.FhirContext;
import com.fhir.service.FhirResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;

@RestController
@RequestMapping("/api/medication-requests")
@Tag(name = "MedicationRequest Resource", description = "Dedicated MedicationRequest resource operations")
public class MedicationRequestController {

    private static final Logger logger = LoggerFactory.getLogger(MedicationRequestController.class);
    private static final String RESOURCE_TYPE = "MedicationRequest";

    @Autowired
    private FhirResourceService resourceService;

    @Autowired
    private FhirContext fhirContext;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a MedicationRequest", description = "Creates a new MedicationRequest resource")
    public ResponseEntity<String> create(@RequestBody String medicationRequestJson) {
        logger.info("Creating new MedicationRequest");

        MedicationRequest medicationRequest = fhirContext.newJsonParser().parseResource(MedicationRequest.class, medicationRequestJson);
        MedicationRequest created = resourceService.create(medicationRequest);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .header("Location", "/api/medication-requests/" + created.getIdElement().getIdPart())
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(created));
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Read a MedicationRequest", description = "Retrieves a MedicationRequest by ID")
    public ResponseEntity<String> read(@PathVariable String id) {
        logger.debug("Reading MedicationRequest with id: {}", id);

        MedicationRequest medicationRequest = resourceService.read(RESOURCE_TYPE, id);

        return ResponseEntity.ok()
                .header("ETag", "W/\"" + medicationRequest.getMeta().getVersionId() + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(medicationRequest));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Search MedicationRequests", description = "Search all MedicationRequests")
    public ResponseEntity<String> search(
            @RequestParam(value = "_page", defaultValue = "0") int page,
            @RequestParam(value = "_count", defaultValue = "20") int count) {

        logger.debug("Searching MedicationRequests");

        Bundle bundle = resourceService.search(RESOURCE_TYPE, new HashMap<>(), page, count);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(bundle));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update a MedicationRequest", description = "Updates an existing MedicationRequest")
    public ResponseEntity<String> update(@PathVariable String id, @RequestBody String medicationRequestJson) {
        logger.info("Updating MedicationRequest with id: {}", id);

        boolean exists = resourceService.resourceExists(RESOURCE_TYPE, id);
        MedicationRequest medicationRequest = fhirContext.newJsonParser().parseResource(MedicationRequest.class, medicationRequestJson);
        MedicationRequest updated = resourceService.update(RESOURCE_TYPE, id, medicationRequest);

        return ResponseEntity
                .status(exists ? HttpStatus.OK : HttpStatus.CREATED)
                .header("ETag", "W/\"" + updated.getMeta().getVersionId() + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(updated));
    }

    @DeleteMapping(value = "/{id}")
    @Operation(summary = "Delete a MedicationRequest", description = "Deletes a MedicationRequest by ID")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        logger.info("Deleting MedicationRequest with id: {}", id);

        resourceService.delete(RESOURCE_TYPE, id);

        return ResponseEntity.noContent().build();
    }
}
