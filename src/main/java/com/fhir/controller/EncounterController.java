package com.fhir.controller;

import ca.uhn.fhir.context.FhirContext;
import com.fhir.service.FhirResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Encounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;

@RestController
@RequestMapping("/api/encounters")
@Tag(name = "Encounter Resource", description = "Dedicated Encounter resource operations")
public class EncounterController {

    private static final Logger logger = LoggerFactory.getLogger(EncounterController.class);
    private static final String RESOURCE_TYPE = "Encounter";

    @Autowired
    private FhirResourceService resourceService;

    @Autowired
    private FhirContext fhirContext;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create an Encounter", description = "Creates a new Encounter resource")
    public ResponseEntity<String> create(@RequestBody String encounterJson) {
        logger.info("Creating new Encounter");

        Encounter encounter = fhirContext.newJsonParser().parseResource(Encounter.class, encounterJson);
        Encounter created = resourceService.create(encounter);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .header("Location", "/api/encounters/" + created.getIdElement().getIdPart())
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(created));
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Read an Encounter", description = "Retrieves an Encounter by ID")
    public ResponseEntity<String> read(@PathVariable String id) {
        logger.debug("Reading Encounter with id: {}", id);

        Encounter encounter = resourceService.read(RESOURCE_TYPE, id);

        return ResponseEntity.ok()
                .header("ETag", "W/\"" + encounter.getMeta().getVersionId() + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(encounter));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Search Encounters", description = "Search all Encounters")
    public ResponseEntity<String> search(
            @RequestParam(value = "_page", defaultValue = "0") int page,
            @RequestParam(value = "_count", defaultValue = "20") int count) {

        logger.debug("Searching Encounters");

        Bundle bundle = resourceService.search(RESOURCE_TYPE, new HashMap<>(), page, count);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(bundle));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update an Encounter", description = "Updates an existing Encounter")
    public ResponseEntity<String> update(@PathVariable String id, @RequestBody String encounterJson) {
        logger.info("Updating Encounter with id: {}", id);

        boolean exists = resourceService.resourceExists(RESOURCE_TYPE, id);
        Encounter encounter = fhirContext.newJsonParser().parseResource(Encounter.class, encounterJson);
        Encounter updated = resourceService.update(RESOURCE_TYPE, id, encounter);

        return ResponseEntity
                .status(exists ? HttpStatus.OK : HttpStatus.CREATED)
                .header("ETag", "W/\"" + updated.getMeta().getVersionId() + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(updated));
    }

    @DeleteMapping(value = "/{id}")
    @Operation(summary = "Delete an Encounter", description = "Deletes an Encounter by ID")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        logger.info("Deleting Encounter with id: {}", id);

        resourceService.delete(RESOURCE_TYPE, id);

        return ResponseEntity.noContent().build();
    }
}
