package com.fhir.controller;

import ca.uhn.fhir.context.FhirContext;
import com.fhir.service.FhirResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Organization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;

@RestController
@RequestMapping("/api/organizations")
@Tag(name = "Organization Resource", description = "Dedicated Organization resource operations")
public class OrganizationController {

    private static final Logger logger = LoggerFactory.getLogger(OrganizationController.class);
    private static final String RESOURCE_TYPE = "Organization";

    @Autowired
    private FhirResourceService resourceService;

    @Autowired
    private FhirContext fhirContext;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create an Organization", description = "Creates a new Organization resource")
    public ResponseEntity<String> create(@RequestBody String organizationJson) {
        logger.info("Creating new Organization");

        Organization organization = fhirContext.newJsonParser().parseResource(Organization.class, organizationJson);
        Organization created = resourceService.create(organization);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .header("Location", "/api/organizations/" + created.getIdElement().getIdPart())
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(created));
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Read an Organization", description = "Retrieves an Organization by ID")
    public ResponseEntity<String> read(@PathVariable String id) {
        logger.debug("Reading Organization with id: {}", id);

        Organization organization = resourceService.read(RESOURCE_TYPE, id);

        return ResponseEntity.ok()
                .header("ETag", "W/\"" + organization.getMeta().getVersionId() + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(organization));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Search Organizations", description = "Search all Organizations")
    public ResponseEntity<String> search(
            @RequestParam(value = "_page", defaultValue = "0") int page,
            @RequestParam(value = "_count", defaultValue = "20") int count) {

        logger.debug("Searching Organizations");

        Bundle bundle = resourceService.search(RESOURCE_TYPE, new HashMap<>(), page, count);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(bundle));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update an Organization", description = "Updates an existing Organization")
    public ResponseEntity<String> update(@PathVariable String id, @RequestBody String organizationJson) {
        logger.info("Updating Organization with id: {}", id);

        boolean exists = resourceService.resourceExists(RESOURCE_TYPE, id);
        Organization organization = fhirContext.newJsonParser().parseResource(Organization.class, organizationJson);
        Organization updated = resourceService.update(RESOURCE_TYPE, id, organization);

        return ResponseEntity
                .status(exists ? HttpStatus.OK : HttpStatus.CREATED)
                .header("ETag", "W/\"" + updated.getMeta().getVersionId() + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(updated));
    }

    @DeleteMapping(value = "/{id}")
    @Operation(summary = "Delete an Organization", description = "Deletes an Organization by ID")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        logger.info("Deleting Organization with id: {}", id);

        resourceService.delete(RESOURCE_TYPE, id);

        return ResponseEntity.noContent().build();
    }
}
