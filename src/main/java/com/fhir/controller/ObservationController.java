package com.fhir.controller;

import ca.uhn.fhir.context.FhirContext;
import com.fhir.model.CursorPage;
import com.fhir.model.FhirResourceDocument;
import com.fhir.service.FhirResourceService;
import com.fhir.service.FhirSearchService;
import com.fhir.util.CompressionUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/observations")
@Tag(name = "Observation Resource", description = "Dedicated Observation resource operations")
public class ObservationController {

    private static final Logger logger = LoggerFactory.getLogger(ObservationController.class);
    private static final String RESOURCE_TYPE = "Observation";

    @Value("${fhir.server.base-url:http://localhost:8080}")
    private String baseUrl;

    @Autowired
    private FhirResourceService resourceService;

    @Autowired
    private FhirSearchService searchService;

    @Autowired
    private FhirContext fhirContext;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create an Observation", description = "Creates a new Observation resource")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Observation created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid Observation format")
    })
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
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Observation found"),
            @ApiResponse(responseCode = "404", description = "Observation not found")
    })
    public ResponseEntity<String> read(@PathVariable String id) {
        logger.debug("Reading Observation with id: {}", id);

        Observation observation = resourceService.read(RESOURCE_TYPE, id);

        return ResponseEntity.ok()
                .header("ETag", "W/\"" + observation.getMeta().getVersionId() + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(observation));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Search Observations", description = "Search Observations with cursor-based pagination for O(1) performance")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Search results returned as Bundle with cursor links")
    })
    public ResponseEntity<String> search(
            @Parameter(description = "Cursor for pagination (ID from previous page's next link)")
            @RequestParam(value = "_cursor", required = false) String cursor,
            @Parameter(description = "Number of results per page (default: 20, max: 100)")
            @RequestParam(value = "_count", defaultValue = "20") int count,
            @Parameter(description = "Patient reference") @RequestParam(required = false) String patient,
            @Parameter(description = "Observation code") @RequestParam(required = false) String code,
            @Parameter(description = "Category") @RequestParam(required = false) String category,
            @Parameter(description = "Subject reference") @RequestParam(required = false) String subject,
            @Parameter(description = "Encounter reference") @RequestParam(required = false) String encounter,
            @Parameter(description = "Date") @RequestParam(required = false) String date) {

        logger.debug("Searching Observations with cursor: {}", cursor);

        // Limit count to reasonable maximum
        int effectiveCount = Math.min(Math.max(count, 1), 100);

        Map<String, String> searchParams = new HashMap<>();
        if (patient != null) searchParams.put("patient", patient);
        if (code != null) searchParams.put("code", code);
        if (category != null) searchParams.put("category", category);
        if (subject != null) searchParams.put("subject", subject);
        if (encounter != null) searchParams.put("encounter", encounter);
        if (date != null) searchParams.put("date", date);

        CursorPage<FhirResourceDocument> cursorPage;

        if (searchParams.isEmpty()) {
            cursorPage = resourceService.searchWithCursor(RESOURCE_TYPE, cursor, effectiveCount);
        } else {
            cursorPage = searchService.searchWithCursor(RESOURCE_TYPE, searchParams, cursor, effectiveCount);
        }

        Bundle bundle = createCursorBundle(cursorPage, cursor, effectiveCount);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(bundle));
    }

    private Bundle createCursorBundle(CursorPage<FhirResourceDocument> cursorPage, String currentCursor, int count) {
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.SEARCHSET);

        // Add entries
        for (FhirResourceDocument doc : cursorPage.getContent()) {
            Bundle.BundleEntryComponent entry = bundle.addEntry();
            String json = getResourceJson(doc);
            IBaseResource resource = fhirContext.newJsonParser().parseResource(json);
            entry.setResource((Resource) resource);
            entry.setFullUrl(baseUrl + "/api/observations/" + doc.getResourceId());

            Bundle.BundleEntrySearchComponent search = new Bundle.BundleEntrySearchComponent();
            search.setMode(Bundle.SearchEntryMode.MATCH);
            entry.setSearch(search);
        }

        // Set total if available
        if (cursorPage.getEstimatedTotal() != null) {
            bundle.setTotal(cursorPage.getEstimatedTotal().intValue());
        }

        // Add self link
        String selfUrl = baseUrl + "/api/observations?_count=" + count;
        if (currentCursor != null && !currentCursor.isEmpty()) {
            selfUrl += "&_cursor=" + currentCursor;
        }
        bundle.addLink().setRelation("self").setUrl(selfUrl);

        // Add next link if more results available
        if (cursorPage.isHasNext() && cursorPage.getNextCursor() != null) {
            String nextUrl = baseUrl + "/api/observations?_count=" + count + "&_cursor=" + cursorPage.getNextCursor();
            bundle.addLink().setRelation("next").setUrl(nextUrl);
        }

        // Add first link (no cursor = first page)
        bundle.addLink().setRelation("first").setUrl(baseUrl + "/api/observations?_count=" + count);

        return bundle;
    }

    private String getResourceJson(FhirResourceDocument doc) {
        if (doc.getIsCompressed() != null && doc.getIsCompressed() && doc.getCompressedJson() != null) {
            return CompressionUtil.decompress(doc.getCompressedJson());
        }
        return doc.getResourceJson();
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update an Observation", description = "Updates an existing Observation")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Observation updated"),
            @ApiResponse(responseCode = "201", description = "Observation created"),
            @ApiResponse(responseCode = "400", description = "Invalid Observation format")
    })
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
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Observation deleted"),
            @ApiResponse(responseCode = "404", description = "Observation not found")
    })
    public ResponseEntity<Void> delete(@PathVariable String id) {
        logger.info("Deleting Observation with id: {}", id);

        resourceService.delete(RESOURCE_TYPE, id);

        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/{id}/_history", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get Observation history", description = "Retrieves the version history of an Observation")
    public ResponseEntity<String> getObservationHistory(
            @PathVariable String id,
            @RequestParam(value = "_page", defaultValue = "0") int page,
            @RequestParam(value = "_count", defaultValue = "20") int count) {

        logger.debug("Getting history for Observation with id: {}", id);

        Bundle bundle = resourceService.history(RESOURCE_TYPE, id, page, count);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(bundle));
    }

    @GetMapping(value = "/{id}/_history/{versionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Read specific Observation version", description = "Retrieves a specific version of an Observation")
    public ResponseEntity<String> getObservationVersion(
            @PathVariable String id,
            @PathVariable String versionId) {

        logger.debug("Reading Observation with id: {} version: {}", id, versionId);

        Observation observation = resourceService.vread(RESOURCE_TYPE, id, versionId);

        return ResponseEntity.ok()
                .header("ETag", "W/\"" + versionId + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(observation));
    }
}
