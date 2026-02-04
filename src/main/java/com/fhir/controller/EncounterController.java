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
import org.hl7.fhir.r4.model.Encounter;
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
@RequestMapping("/api/encounters")
@Tag(name = "Encounter Resource", description = "Dedicated Encounter resource operations")
public class EncounterController {

    private static final Logger logger = LoggerFactory.getLogger(EncounterController.class);
    private static final String RESOURCE_TYPE = "Encounter";

    @Autowired
    private FhirResourceService resourceService;

    @Autowired
    private FhirSearchService searchService;

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
            @RequestParam(value = "_count", defaultValue = "20") int count,
            @Parameter(description = "Patient reference") @RequestParam(required = false) String patient,
            @Parameter(description = "Subject reference") @RequestParam(required = false) String subject,
            @Parameter(description = "Encounter class") @RequestParam(value = "class", required = false) String encounterClass,
            @Parameter(description = "Encounter type") @RequestParam(required = false) String type,
            @Parameter(description = "Status") @RequestParam(required = false) String status,
            @Parameter(description = "Date") @RequestParam(required = false) String date) {

        logger.debug("Searching Encounters");

        Map<String, String> searchParams = new HashMap<>();
        if (patient != null) searchParams.put("patient", patient);
        if (subject != null) searchParams.put("subject", subject);
        if (encounterClass != null) searchParams.put("class", encounterClass);
        if (type != null) searchParams.put("type", type);
        if (status != null) searchParams.put("status", status);
        if (date != null) searchParams.put("date", date);

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

    private Bundle createSearchBundle(Page<FhirResourceDocument> results) {
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.SEARCHSET);
        bundle.setTotal((int) results.getTotalElements());

        for (FhirResourceDocument doc : results.getContent()) {
            Bundle.BundleEntryComponent entry = bundle.addEntry();
            String json = getResourceJson(doc);
            IBaseResource resource = fhirContext.newJsonParser().parseResource(json);
            entry.setResource((Resource) resource);
            entry.setFullUrl("/api/encounters/" + doc.getResourceId());

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
