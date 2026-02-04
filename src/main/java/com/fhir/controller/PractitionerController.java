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
import org.hl7.fhir.r4.model.Practitioner;
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
@RequestMapping("/api/practitioners")
@Tag(name = "Practitioner Resource", description = "Dedicated Practitioner resource operations")
public class PractitionerController {

    private static final Logger logger = LoggerFactory.getLogger(PractitionerController.class);
    private static final String RESOURCE_TYPE = "Practitioner";

    @Autowired
    private FhirResourceService resourceService;

    @Autowired
    private FhirSearchService searchService;

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
            @RequestParam(value = "_count", defaultValue = "20") int count,
            @Parameter(description = "Practitioner name") @RequestParam(required = false) String name,
            @Parameter(description = "Family name") @RequestParam(required = false) String family,
            @Parameter(description = "Given name") @RequestParam(required = false) String given,
            @Parameter(description = "Identifier") @RequestParam(required = false) String identifier,
            @Parameter(description = "Phone") @RequestParam(required = false) String phone,
            @Parameter(description = "Email") @RequestParam(required = false) String email,
            @Parameter(description = "Active status") @RequestParam(required = false) String active) {

        logger.debug("Searching Practitioners");

        Map<String, String> searchParams = new HashMap<>();
        if (name != null) searchParams.put("name", name);
        if (family != null) searchParams.put("family", family);
        if (given != null) searchParams.put("given", given);
        if (identifier != null) searchParams.put("identifier", identifier);
        if (phone != null) searchParams.put("phone", phone);
        if (email != null) searchParams.put("email", email);
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

    private Bundle createSearchBundle(Page<FhirResourceDocument> results) {
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.SEARCHSET);
        bundle.setTotal((int) results.getTotalElements());

        for (FhirResourceDocument doc : results.getContent()) {
            Bundle.BundleEntryComponent entry = bundle.addEntry();
            String json = getResourceJson(doc);
            IBaseResource resource = fhirContext.newJsonParser().parseResource(json);
            entry.setResource((Resource) resource);
            entry.setFullUrl("/api/practitioners/" + doc.getResourceId());

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
