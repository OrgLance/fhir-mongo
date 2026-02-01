package com.fhir.controller;

import ca.uhn.fhir.context.FhirContext;
import com.fhir.service.FhirResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Practitioner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;

@RestController
@RequestMapping("/api/practitioners")
@Tag(name = "Practitioner Resource", description = "Dedicated Practitioner resource operations")
public class PractitionerController {

    private static final Logger logger = LoggerFactory.getLogger(PractitionerController.class);
    private static final String RESOURCE_TYPE = "Practitioner";

    @Autowired
    private FhirResourceService resourceService;

    @Autowired
    private FhirContext fhirContext;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a Practitioner", description = "Creates a new Practitioner resource")
    public ResponseEntity<String> create(@RequestBody String practitionerJson) {
        logger.info("Creating new Practitioner");

        Practitioner practitioner = fhirContext.newJsonParser().parseResource(Practitioner.class, practitionerJson);
        Practitioner created = resourceService.create(practitioner);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .header("Location", "/api/practitioners/" + created.getIdElement().getIdPart())
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(created));
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Read a Practitioner", description = "Retrieves a Practitioner by ID")
    public ResponseEntity<String> read(@PathVariable String id) {
        logger.debug("Reading Practitioner with id: {}", id);

        Practitioner practitioner = resourceService.read(RESOURCE_TYPE, id);

        return ResponseEntity.ok()
                .header("ETag", "W/\"" + practitioner.getMeta().getVersionId() + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(practitioner));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Search Practitioners", description = "Search all Practitioners")
    public ResponseEntity<String> search(
            @RequestParam(value = "_page", defaultValue = "0") int page,
            @RequestParam(value = "_count", defaultValue = "20") int count) {

        logger.debug("Searching Practitioners");

        Bundle bundle = resourceService.search(RESOURCE_TYPE, new HashMap<>(), page, count);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(bundle));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update a Practitioner", description = "Updates an existing Practitioner")
    public ResponseEntity<String> update(@PathVariable String id, @RequestBody String practitionerJson) {
        logger.info("Updating Practitioner with id: {}", id);

        boolean exists = resourceService.resourceExists(RESOURCE_TYPE, id);
        Practitioner practitioner = fhirContext.newJsonParser().parseResource(Practitioner.class, practitionerJson);
        Practitioner updated = resourceService.update(RESOURCE_TYPE, id, practitioner);

        return ResponseEntity
                .status(exists ? HttpStatus.OK : HttpStatus.CREATED)
                .header("ETag", "W/\"" + updated.getMeta().getVersionId() + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(updated));
    }

    @DeleteMapping(value = "/{id}")
    @Operation(summary = "Delete a Practitioner", description = "Deletes a Practitioner by ID")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        logger.info("Deleting Practitioner with id: {}", id);

        resourceService.delete(RESOURCE_TYPE, id);

        return ResponseEntity.noContent().build();
    }
}
