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
import org.hl7.fhir.r4.model.Condition;
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
@RequestMapping("/api/conditions")
@Tag(name = "Condition Resource", description = "Dedicated Condition resource operations")
public class ConditionController {

    private static final Logger logger = LoggerFactory.getLogger(ConditionController.class);
    private static final String RESOURCE_TYPE = "Condition";

    @Value("${fhir.server.base-url:http://localhost:8080}")
    private String baseUrl;

    @Autowired
    private FhirResourceService resourceService;

    @Autowired
    private FhirSearchService searchService;

    @Autowired
    private FhirContext fhirContext;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a Condition", description = "Creates a new Condition resource")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Condition created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid Condition format")
    })
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
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Condition found"),
            @ApiResponse(responseCode = "404", description = "Condition not found")
    })
    public ResponseEntity<String> read(@PathVariable String id) {
        logger.debug("Reading Condition with id: {}", id);

        Condition condition = resourceService.read(RESOURCE_TYPE, id);

        return ResponseEntity.ok()
                .header("ETag", "W/\"" + condition.getMeta().getVersionId() + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(condition));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Search Conditions", description = "Search Conditions with cursor-based pagination for O(1) performance")
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
            @Parameter(description = "Condition code") @RequestParam(required = false) String code,
            @Parameter(description = "Clinical status") @RequestParam(value = "clinical-status", required = false) String clinicalStatus,
            @Parameter(description = "Verification status") @RequestParam(value = "verification-status", required = false) String verificationStatus,
            @Parameter(description = "Category") @RequestParam(required = false) String category,
            @Parameter(description = "Encounter reference") @RequestParam(required = false) String encounter) {

        logger.debug("Searching Conditions with cursor: {}", cursor);

        // Limit count to reasonable maximum
        int effectiveCount = Math.min(Math.max(count, 1), 100);

        Map<String, String> searchParams = new HashMap<>();
        if (patient != null) searchParams.put("patient", patient);
        if (subject != null) searchParams.put("subject", subject);
        if (code != null) searchParams.put("code", code);
        if (clinicalStatus != null) searchParams.put("clinical-status", clinicalStatus);
        if (verificationStatus != null) searchParams.put("verification-status", verificationStatus);
        if (category != null) searchParams.put("category", category);
        if (encounter != null) searchParams.put("encounter", encounter);

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
            entry.setFullUrl(baseUrl + "/api/conditions/" + doc.getResourceId());

            Bundle.BundleEntrySearchComponent search = new Bundle.BundleEntrySearchComponent();
            search.setMode(Bundle.SearchEntryMode.MATCH);
            entry.setSearch(search);
        }

        // Set total if available
        if (cursorPage.getEstimatedTotal() != null) {
            bundle.setTotal(cursorPage.getEstimatedTotal().intValue());
        }

        // Add self link
        String selfUrl = baseUrl + "/api/conditions?_count=" + count;
        if (currentCursor != null && !currentCursor.isEmpty()) {
            selfUrl += "&_cursor=" + currentCursor;
        }
        bundle.addLink().setRelation("self").setUrl(selfUrl);

        // Add next link if more results available
        if (cursorPage.isHasNext() && cursorPage.getNextCursor() != null) {
            String nextUrl = baseUrl + "/api/conditions?_count=" + count + "&_cursor=" + cursorPage.getNextCursor();
            bundle.addLink().setRelation("next").setUrl(nextUrl);
        }

        // Add first link (no cursor = first page)
        bundle.addLink().setRelation("first").setUrl(baseUrl + "/api/conditions?_count=" + count);

        return bundle;
    }

    private String getResourceJson(FhirResourceDocument doc) {
        if (doc.getIsCompressed() != null && doc.getIsCompressed() && doc.getCompressedJson() != null) {
            return CompressionUtil.decompress(doc.getCompressedJson());
        }
        return doc.getResourceJson();
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update a Condition", description = "Updates an existing Condition")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Condition updated"),
            @ApiResponse(responseCode = "201", description = "Condition created"),
            @ApiResponse(responseCode = "400", description = "Invalid Condition format")
    })
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
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Condition deleted"),
            @ApiResponse(responseCode = "404", description = "Condition not found")
    })
    public ResponseEntity<Void> delete(@PathVariable String id) {
        logger.info("Deleting Condition with id: {}", id);

        resourceService.delete(RESOURCE_TYPE, id);

        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/{id}/_history", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get Condition history", description = "Retrieves the version history of a Condition")
    public ResponseEntity<String> getConditionHistory(
            @PathVariable String id,
            @RequestParam(value = "_page", defaultValue = "0") int page,
            @RequestParam(value = "_count", defaultValue = "20") int count) {

        logger.debug("Getting history for Condition with id: {}", id);

        Bundle bundle = resourceService.history(RESOURCE_TYPE, id, page, count);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(bundle));
    }

    @GetMapping(value = "/{id}/_history/{versionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Read specific Condition version", description = "Retrieves a specific version of a Condition")
    public ResponseEntity<String> getConditionVersion(
            @PathVariable String id,
            @PathVariable String versionId) {

        logger.debug("Reading Condition with id: {} version: {}", id, versionId);

        Condition condition = resourceService.vread(RESOURCE_TYPE, id, versionId);

        return ResponseEntity.ok()
                .header("ETag", "W/\"" + versionId + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(condition));
    }
}
