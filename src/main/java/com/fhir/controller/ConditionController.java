package com.fhir.controller;

import ca.uhn.fhir.context.FhirContext;
import com.fhir.service.FhirResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;

@RestController
@RequestMapping("/api/conditions")
@Tag(name = "Condition Resource", description = "Dedicated Condition resource operations")
public class ConditionController {

    private static final Logger logger = LoggerFactory.getLogger(ConditionController.class);
    private static final String RESOURCE_TYPE = "Condition";

    @Autowired
    private FhirResourceService resourceService;

    @Autowired
    private FhirContext fhirContext;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a Condition", description = "Creates a new Condition resource")
    public ResponseEntity<String> create(@RequestBody String conditionJson) {
        logger.info("Creating new Condition");

        Condition condition = fhirContext.newJsonParser().parseResource(Condition.class, conditionJson);
        Condition created = resourceService.create(condition);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .header("Location", "/api/conditions/" + created.getIdElement().getIdPart())
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(created));
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Read a Condition", description = "Retrieves a Condition by ID")
    public ResponseEntity<String> read(@PathVariable String id) {
        logger.debug("Reading Condition with id: {}", id);

        Condition condition = resourceService.read(RESOURCE_TYPE, id);

        return ResponseEntity.ok()
                .header("ETag", "W/\"" + condition.getMeta().getVersionId() + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(condition));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Search Conditions", description = "Search all Conditions")
    public ResponseEntity<String> search(
            @RequestParam(value = "_page", defaultValue = "0") int page,
            @RequestParam(value = "_count", defaultValue = "20") int count) {

        logger.debug("Searching Conditions");

        Bundle bundle = resourceService.search(RESOURCE_TYPE, new HashMap<>(), page, count);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(bundle));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update a Condition", description = "Updates an existing Condition")
    public ResponseEntity<String> update(@PathVariable String id, @RequestBody String conditionJson) {
        logger.info("Updating Condition with id: {}", id);

        boolean exists = resourceService.resourceExists(RESOURCE_TYPE, id);
        Condition condition = fhirContext.newJsonParser().parseResource(Condition.class, conditionJson);
        Condition updated = resourceService.update(RESOURCE_TYPE, id, condition);

        return ResponseEntity
                .status(exists ? HttpStatus.OK : HttpStatus.CREATED)
                .header("ETag", "W/\"" + updated.getMeta().getVersionId() + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(updated));
    }

    @DeleteMapping(value = "/{id}")
    @Operation(summary = "Delete a Condition", description = "Deletes a Condition by ID")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        logger.info("Deleting Condition with id: {}", id);

        resourceService.delete(RESOURCE_TYPE, id);

        return ResponseEntity.noContent().build();
    }
}
