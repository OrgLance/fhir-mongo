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
import org.hl7.fhir.r4.model.Encounter;
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
@RequestMapping("/api/encounters")
@Tag(name = "Encounter Resource", description = "Dedicated Encounter resource operations")
public class EncounterController {

    private static final Logger logger = LoggerFactory.getLogger(EncounterController.class);
    private static final String RESOURCE_TYPE = "Encounter";

    @Value("${fhir.server.base-url:http://localhost:8080}")
    private String baseUrl;

    @Autowired
    private FhirResourceService resourceService;

    @Autowired
    private FhirSearchService searchService;

    @Autowired
    private FhirContext fhirContext;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create an Encounter", description = "Creates a new Encounter resource")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Encounter created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid Encounter format")
    })
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
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Encounter found"),
            @ApiResponse(responseCode = "404", description = "Encounter not found")
    })
    public ResponseEntity<String> read(@PathVariable String id) {
        logger.debug("Reading Encounter with id: {}", id);

        Encounter encounter = resourceService.read(RESOURCE_TYPE, id);

        return ResponseEntity.ok()
                .header("ETag", "W/\"" + encounter.getMeta().getVersionId() + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(encounter));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Search Encounters", description = "Search Encounters with cursor-based pagination for O(1) performance")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Search results returned as Bundle with cursor links")
    })
    public ResponseEntity<String> search(
            @Parameter(description = "Cursor for pagination (ID from previous page's next link)")
            @RequestParam(value = "_cursor", required = false) String cursor,
            @Parameter(description = "Number of results per page (default: 20, max: 100)")
            @RequestParam(value = "_count", defaultValue = "20") int count,
            @Parameter(description = "Patient reference") @RequestParam(required = false) String patient,
            @Parameter(description = "Subject reference") @RequestParam(required = false) String subject,
            @Parameter(description = "Encounter class") @RequestParam(value = "class", required = false) String encounterClass,
            @Parameter(description = "Encounter type") @RequestParam(required = false) String type,
            @Parameter(description = "Status") @RequestParam(required = false) String status,
            @Parameter(description = "Date") @RequestParam(required = false) String date) {

        logger.debug("Searching Encounters with cursor: {}", cursor);

        // Limit count to reasonable maximum
        int effectiveCount = Math.min(Math.max(count, 1), 100);

        Map<String, String> searchParams = new HashMap<>();
        if (patient != null) searchParams.put("patient", patient);
        if (subject != null) searchParams.put("subject", subject);
        if (encounterClass != null) searchParams.put("class", encounterClass);
        if (type != null) searchParams.put("type", type);
        if (status != null) searchParams.put("status", status);
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
            entry.setFullUrl(baseUrl + "/api/encounters/" + doc.getResourceId());

            Bundle.BundleEntrySearchComponent search = new Bundle.BundleEntrySearchComponent();
            search.setMode(Bundle.SearchEntryMode.MATCH);
            entry.setSearch(search);
        }

        // Set total if available
        if (cursorPage.getEstimatedTotal() != null) {
            bundle.setTotal(cursorPage.getEstimatedTotal().intValue());
        }

        // Add self link
        String selfUrl = baseUrl + "/api/encounters?_count=" + count;
        if (currentCursor != null && !currentCursor.isEmpty()) {
            selfUrl += "&_cursor=" + currentCursor;
        }
        bundle.addLink().setRelation("self").setUrl(selfUrl);

        // Add next link if more results available
        if (cursorPage.isHasNext() && cursorPage.getNextCursor() != null) {
            String nextUrl = baseUrl + "/api/encounters?_count=" + count + "&_cursor=" + cursorPage.getNextCursor();
            bundle.addLink().setRelation("next").setUrl(nextUrl);
        }

        // Add first link (no cursor = first page)
        bundle.addLink().setRelation("first").setUrl(baseUrl + "/api/encounters?_count=" + count);

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
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Encounter updated"),
            @ApiResponse(responseCode = "201", description = "Encounter created"),
            @ApiResponse(responseCode = "400", description = "Invalid Encounter format")
    })
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
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Encounter deleted"),
            @ApiResponse(responseCode = "404", description = "Encounter not found")
    })
    public ResponseEntity<Void> delete(@PathVariable String id) {
        logger.info("Deleting Encounter with id: {}", id);

        resourceService.delete(RESOURCE_TYPE, id);

        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/{id}/_history", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get Encounter history", description = "Retrieves the version history of an Encounter")
    public ResponseEntity<String> getEncounterHistory(
            @PathVariable String id,
            @RequestParam(value = "_page", defaultValue = "0") int page,
            @RequestParam(value = "_count", defaultValue = "20") int count) {

        logger.debug("Getting history for Encounter with id: {}", id);

        Bundle bundle = resourceService.history(RESOURCE_TYPE, id, page, count);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(bundle));
    }

    @GetMapping(value = "/{id}/_history/{versionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Read specific Encounter version", description = "Retrieves a specific version of an Encounter")
    public ResponseEntity<String> getEncounterVersion(
            @PathVariable String id,
            @PathVariable String versionId) {

        logger.debug("Reading Encounter with id: {} version: {}", id, versionId);

        Encounter encounter = resourceService.vread(RESOURCE_TYPE, id, versionId);

        return ResponseEntity.ok()
                .header("ETag", "W/\"" + versionId + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(encounter));
    }
}
