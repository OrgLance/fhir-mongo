package com.fhir.controller;

import ca.uhn.fhir.context.FhirContext;
import com.fhir.model.FhirResourceDocument;
import com.fhir.service.FhirResourceService;
import com.fhir.service.FhirSearchService;
import com.fhir.util.CompressionUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/organizations")
@Tag(name = "Organization Resource", description = "Dedicated Organization resource operations")
public class OrganizationController {

    private static final Logger logger = LoggerFactory.getLogger(OrganizationController.class);
    private static final String RESOURCE_TYPE = "Organization";

    @Autowired
    private FhirResourceService resourceService;

    @Autowired
    private FhirSearchService searchService;

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
            @RequestParam(value = "_count", defaultValue = "20") int count,
            @Parameter(description = "Organization name") @RequestParam(required = false) String name,
            @Parameter(description = "Organization type") @RequestParam(required = false) String type,
            @Parameter(description = "Identifier") @RequestParam(required = false) String identifier,
            @Parameter(description = "Address") @RequestParam(required = false) String address,
            @Parameter(description = "Address city") @RequestParam(value = "address-city", required = false) String addressCity,
            @Parameter(description = "Address state") @RequestParam(value = "address-state", required = false) String addressState,
            @Parameter(description = "Part of organization") @RequestParam(required = false) String partof,
            @Parameter(description = "Active status") @RequestParam(required = false) String active) {

        logger.debug("Searching Organizations");

        Map<String, String> searchParams = new HashMap<>();
        if (name != null) searchParams.put("name", name);
        if (type != null) searchParams.put("type", type);
        if (identifier != null) searchParams.put("identifier", identifier);
        if (address != null) searchParams.put("address", address);
        if (addressCity != null) searchParams.put("address-city", addressCity);
        if (addressState != null) searchParams.put("address-state", addressState);
        if (partof != null) searchParams.put("partof", partof);
        if (active != null) searchParams.put("active", active);

        if (searchParams.isEmpty()) {
            Bundle bundle = resourceService.search(RESOURCE_TYPE, searchParams, page, count);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(fhirContext.newJsonParser().encodeResourceToString(bundle));
        }

        Page<FhirResourceDocument> results = searchService.search(
                RESOURCE_TYPE,
                searchParams,
                PageRequest.of(page, count, Sort.by(Sort.Direction.DESC, "lastUpdated"))
        );

        Bundle bundle = createSearchBundle(results);

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

    private Bundle createSearchBundle(Page<FhirResourceDocument> results) {
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.SEARCHSET);
        bundle.setTotal((int) results.getTotalElements());

        for (FhirResourceDocument doc : results.getContent()) {
            Bundle.BundleEntryComponent entry = bundle.addEntry();
            String json = getResourceJson(doc);
            IBaseResource resource = fhirContext.newJsonParser().parseResource(json);
            entry.setResource((Resource) resource);
            entry.setFullUrl("/api/organizations/" + doc.getResourceId());

            Bundle.BundleEntrySearchComponent search = new Bundle.BundleEntrySearchComponent();
            search.setMode(Bundle.SearchEntryMode.MATCH);
            entry.setSearch(search);
        }

        return bundle;
    }

    private String getResourceJson(FhirResourceDocument doc) {
        if (doc.getIsCompressed() != null && doc.getIsCompressed() && doc.getCompressedJson() != null) {
            return CompressionUtil.decompress(doc.getCompressedJson());
        }
        return doc.getResourceJson();
    }
}
