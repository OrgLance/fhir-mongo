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
import org.hl7.fhir.r4.model.Practitioner;
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
@RequestMapping("/api/practitioners")
@Tag(name = "Practitioner Resource", description = "Dedicated Practitioner resource operations")
public class PractitionerController {

    private static final Logger logger = LoggerFactory.getLogger(PractitionerController.class);
    private static final String RESOURCE_TYPE = "Practitioner";

    @Value("${fhir.server.base-url:http://localhost:8080}")
    private String baseUrl;

    @Autowired
    private FhirResourceService resourceService;

    @Autowired
    private FhirSearchService searchService;

    @Autowired
    private FhirContext fhirContext;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a Practitioner", description = "Creates a new Practitioner resource")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Practitioner created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid Practitioner format")
    })
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
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Practitioner found"),
            @ApiResponse(responseCode = "404", description = "Practitioner not found")
    })
    public ResponseEntity<String> read(@PathVariable String id) {
        logger.debug("Reading Practitioner with id: {}", id);

        Practitioner practitioner = resourceService.read(RESOURCE_TYPE, id);

        return ResponseEntity.ok()
                .header("ETag", "W/\"" + practitioner.getMeta().getVersionId() + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(practitioner));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Search Practitioners", description = "Search Practitioners with cursor-based pagination for O(1) performance")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Search results returned as Bundle with cursor links")
    })
    public ResponseEntity<String> search(
            @Parameter(description = "Cursor for pagination (ID from previous page's next link)")
            @RequestParam(value = "_cursor", required = false) String cursor,
            @Parameter(description = "Number of results per page (default: 20, max: 100)")
            @RequestParam(value = "_count", defaultValue = "20") int count,
            @Parameter(description = "Practitioner name") @RequestParam(required = false) String name,
            @Parameter(description = "Family name") @RequestParam(required = false) String family,
            @Parameter(description = "Given name") @RequestParam(required = false) String given,
            @Parameter(description = "Identifier") @RequestParam(required = false) String identifier,
            @Parameter(description = "Phone") @RequestParam(required = false) String phone,
            @Parameter(description = "Email") @RequestParam(required = false) String email,
            @Parameter(description = "Active status") @RequestParam(required = false) String active) {

        logger.debug("Searching Practitioners with cursor: {}", cursor);

        // Limit count to reasonable maximum
        int effectiveCount = Math.min(Math.max(count, 1), 100);

        Map<String, String> searchParams = new HashMap<>();
        if (name != null) searchParams.put("name", name);
        if (family != null) searchParams.put("family", family);
        if (given != null) searchParams.put("given", given);
        if (identifier != null) searchParams.put("identifier", identifier);
        if (phone != null) searchParams.put("phone", phone);
        if (email != null) searchParams.put("email", email);
        if (active != null) searchParams.put("active", active);

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
            entry.setFullUrl(baseUrl + "/api/practitioners/" + doc.getResourceId());

            Bundle.BundleEntrySearchComponent search = new Bundle.BundleEntrySearchComponent();
            search.setMode(Bundle.SearchEntryMode.MATCH);
            entry.setSearch(search);
        }

        // Set total if available
        if (cursorPage.getEstimatedTotal() != null) {
            bundle.setTotal(cursorPage.getEstimatedTotal().intValue());
        }

        // Add self link
        String selfUrl = baseUrl + "/api/practitioners?_count=" + count;
        if (currentCursor != null && !currentCursor.isEmpty()) {
            selfUrl += "&_cursor=" + currentCursor;
        }
        bundle.addLink().setRelation("self").setUrl(selfUrl);

        // Add next link if more results available
        if (cursorPage.isHasNext() && cursorPage.getNextCursor() != null) {
            String nextUrl = baseUrl + "/api/practitioners?_count=" + count + "&_cursor=" + cursorPage.getNextCursor();
            bundle.addLink().setRelation("next").setUrl(nextUrl);
        }

        // Add first link (no cursor = first page)
        bundle.addLink().setRelation("first").setUrl(baseUrl + "/api/practitioners?_count=" + count);

        return bundle;
    }

    private String getResourceJson(FhirResourceDocument doc) {
        if (doc.getIsCompressed() != null && doc.getIsCompressed() && doc.getCompressedJson() != null) {
            return CompressionUtil.decompress(doc.getCompressedJson());
        }
        return doc.getResourceJson();
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update a Practitioner", description = "Updates an existing Practitioner")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Practitioner updated"),
            @ApiResponse(responseCode = "201", description = "Practitioner created"),
            @ApiResponse(responseCode = "400", description = "Invalid Practitioner format")
    })
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
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Practitioner deleted"),
            @ApiResponse(responseCode = "404", description = "Practitioner not found")
    })
    public ResponseEntity<Void> delete(@PathVariable String id) {
        logger.info("Deleting Practitioner with id: {}", id);

        resourceService.delete(RESOURCE_TYPE, id);

        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/{id}/_history", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get Practitioner history", description = "Retrieves the version history of a Practitioner")
    public ResponseEntity<String> getPractitionerHistory(
            @PathVariable String id,
            @RequestParam(value = "_page", defaultValue = "0") int page,
            @RequestParam(value = "_count", defaultValue = "20") int count) {

        logger.debug("Getting history for Practitioner with id: {}", id);

        Bundle bundle = resourceService.history(RESOURCE_TYPE, id, page, count);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(bundle));
    }

    @GetMapping(value = "/{id}/_history/{versionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Read specific Practitioner version", description = "Retrieves a specific version of a Practitioner")
    public ResponseEntity<String> getPractitionerVersion(
            @PathVariable String id,
            @PathVariable String versionId) {

        logger.debug("Reading Practitioner with id: {} version: {}", id, versionId);

        Practitioner practitioner = resourceService.vread(RESOURCE_TYPE, id, versionId);

        return ResponseEntity.ok()
                .header("ETag", "W/\"" + versionId + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(practitioner));
    }
}
