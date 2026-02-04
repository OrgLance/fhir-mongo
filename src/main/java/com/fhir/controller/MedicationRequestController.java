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
import org.hl7.fhir.r4.model.MedicationRequest;
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
@RequestMapping("/api/medication-requests")
@Tag(name = "MedicationRequest Resource", description = "Dedicated MedicationRequest resource operations")
public class MedicationRequestController {

    private static final Logger logger = LoggerFactory.getLogger(MedicationRequestController.class);
    private static final String RESOURCE_TYPE = "MedicationRequest";

    @Autowired
    private FhirResourceService resourceService;

    @Autowired
    private FhirSearchService searchService;

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
            @RequestParam(value = "_count", defaultValue = "20") int count,
            @Parameter(description = "Patient reference") @RequestParam(required = false) String patient,
            @Parameter(description = "Subject reference") @RequestParam(required = false) String subject,
            @Parameter(description = "Medication code") @RequestParam(required = false) String code,
            @Parameter(description = "Status") @RequestParam(required = false) String status,
            @Parameter(description = "Intent") @RequestParam(required = false) String intent,
            @Parameter(description = "Requester reference") @RequestParam(required = false) String requester,
            @Parameter(description = "Encounter reference") @RequestParam(required = false) String encounter,
            @Parameter(description = "Authored date") @RequestParam(required = false) String authoredon) {

        logger.debug("Searching MedicationRequests");

        Map<String, String> searchParams = new HashMap<>();
        if (patient != null) searchParams.put("patient", patient);
        if (subject != null) searchParams.put("subject", subject);
        if (code != null) searchParams.put("code", code);
        if (status != null) searchParams.put("status", status);
        if (intent != null) searchParams.put("intent", intent);
        if (requester != null) searchParams.put("requester", requester);
        if (encounter != null) searchParams.put("encounter", encounter);
        if (authoredon != null) searchParams.put("authoredon", authoredon);

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

    private Bundle createSearchBundle(Page<FhirResourceDocument> results) {
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.SEARCHSET);
        bundle.setTotal((int) results.getTotalElements());

        for (FhirResourceDocument doc : results.getContent()) {
            Bundle.BundleEntryComponent entry = bundle.addEntry();
            String json = getResourceJson(doc);
            IBaseResource resource = fhirContext.newJsonParser().parseResource(json);
            entry.setResource((Resource) resource);
            entry.setFullUrl("/api/medication-requests/" + doc.getResourceId());

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
