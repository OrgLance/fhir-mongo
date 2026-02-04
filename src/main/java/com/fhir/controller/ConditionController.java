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
import org.hl7.fhir.r4.model.Condition;
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
@RequestMapping("/api/conditions")
@Tag(name = "Condition Resource", description = "Dedicated Condition resource operations")
public class ConditionController {

    private static final Logger logger = LoggerFactory.getLogger(ConditionController.class);
    private static final String RESOURCE_TYPE = "Condition";

    @Autowired
    private FhirResourceService resourceService;

    @Autowired
    private FhirSearchService searchService;

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
            @RequestParam(value = "_count", defaultValue = "20") int count,
            @Parameter(description = "Patient reference") @RequestParam(required = false) String patient,
            @Parameter(description = "Subject reference") @RequestParam(required = false) String subject,
            @Parameter(description = "Condition code") @RequestParam(required = false) String code,
            @Parameter(description = "Clinical status") @RequestParam(value = "clinical-status", required = false) String clinicalStatus,
            @Parameter(description = "Verification status") @RequestParam(value = "verification-status", required = false) String verificationStatus,
            @Parameter(description = "Category") @RequestParam(required = false) String category,
            @Parameter(description = "Encounter reference") @RequestParam(required = false) String encounter) {

        logger.debug("Searching Conditions");

        Map<String, String> searchParams = new HashMap<>();
        if (patient != null) searchParams.put("patient", patient);
        if (subject != null) searchParams.put("subject", subject);
        if (code != null) searchParams.put("code", code);
        if (clinicalStatus != null) searchParams.put("clinical-status", clinicalStatus);
        if (verificationStatus != null) searchParams.put("verification-status", verificationStatus);
        if (category != null) searchParams.put("category", category);
        if (encounter != null) searchParams.put("encounter", encounter);

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

    private Bundle createSearchBundle(Page<FhirResourceDocument> results) {
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.SEARCHSET);
        bundle.setTotal((int) results.getTotalElements());

        for (FhirResourceDocument doc : results.getContent()) {
            Bundle.BundleEntryComponent entry = bundle.addEntry();
            String json = getResourceJson(doc);
            IBaseResource resource = fhirContext.newJsonParser().parseResource(json);
            entry.setResource((Resource) resource);
            entry.setFullUrl("/api/conditions/" + doc.getResourceId());

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
