package com.fhir.controller;

import ca.uhn.fhir.context.FhirContext;
import com.fhir.service.FhirResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;

@RestController
@RequestMapping("/api/observations")
@Tag(name = "Observation Resource", description = "Dedicated Observation resource operations")
public class ObservationController {

    private static final Logger logger = LoggerFactory.getLogger(ObservationController.class);
    private static final String RESOURCE_TYPE = "Observation";

    @Autowired
    private FhirResourceService resourceService;

    @Autowired
    private FhirContext fhirContext;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create an Observation", description = "Creates a new Observation resource")
    public ResponseEntity<String> create(@RequestBody String observationJson) {
        logger.info("Creating new Observation");

        Observation observation = fhirContext.newJsonParser().parseResource(Observation.class, observationJson);
        Observation created = resourceService.create(observation);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .header("Location", "/api/observations/" + created.getIdElement().getIdPart())
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(created));
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Read an Observation", description = "Retrieves an Observation by ID")
    public ResponseEntity<String> read(@PathVariable String id) {
        logger.debug("Reading Observation with id: {}", id);

        Observation observation = resourceService.read(RESOURCE_TYPE, id);

        return ResponseEntity.ok()
                .header("ETag", "W/\"" + observation.getMeta().getVersionId() + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(observation));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Search Observations", description = "Search all Observations")
    public ResponseEntity<String> search(
            @RequestParam(value = "_page", defaultValue = "0") int page,
            @RequestParam(value = "_count", defaultValue = "20") int count,
            @Parameter(description = "Patient reference") @RequestParam(required = false) String patient,
            @Parameter(description = "Observation code") @RequestParam(required = false) String code,
            @Parameter(description = "Category") @RequestParam(required = false) String category) {

        logger.debug("Searching Observations");

        Bundle bundle = resourceService.search(RESOURCE_TYPE, new HashMap<>(), page, count);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(bundle));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update an Observation", description = "Updates an existing Observation")
    public ResponseEntity<String> update(@PathVariable String id, @RequestBody String observationJson) {
        logger.info("Updating Observation with id: {}", id);

        boolean exists = resourceService.resourceExists(RESOURCE_TYPE, id);
        Observation observation = fhirContext.newJsonParser().parseResource(Observation.class, observationJson);
        Observation updated = resourceService.update(RESOURCE_TYPE, id, observation);

        return ResponseEntity
                .status(exists ? HttpStatus.OK : HttpStatus.CREATED)
                .header("ETag", "W/\"" + updated.getMeta().getVersionId() + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(updated));
    }

    @DeleteMapping(value = "/{id}")
    @Operation(summary = "Delete an Observation", description = "Deletes an Observation by ID")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        logger.info("Deleting Observation with id: {}", id);

        resourceService.delete(RESOURCE_TYPE, id);

        return ResponseEntity.noContent().build();
    }
}
