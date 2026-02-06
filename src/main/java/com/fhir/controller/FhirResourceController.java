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
import jakarta.servlet.http.HttpServletRequest;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/fhir")
@Tag(name = "FHIR Resources", description = "Generic FHIR resource operations - supports all R4 resource types")
public class FhirResourceController {

    private static final Logger logger = LoggerFactory.getLogger(FhirResourceController.class);

    @Value("${fhir.server.base-url:http://localhost:8080/fhir}")
    private String baseUrl;

    @Autowired
    private FhirResourceService resourceService;

    @Autowired
    private FhirSearchService searchService;

    @Autowired
    private FhirContext fhirContext;

    @PostMapping(value = "/{resourceType}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a FHIR resource", description = "Creates a new FHIR resource of the specified type")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Resource created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid resource format"),
            @ApiResponse(responseCode = "422", description = "Validation error")
    })
    public ResponseEntity<String> create(
            @Parameter(description = "FHIR resource type (e.g., Patient, Observation)")
            @PathVariable String resourceType,
            @RequestBody String resourceJson) {

        logger.info("Creating {} resource", resourceType);

        IBaseResource resource = fhirContext.newJsonParser().parseResource(resourceJson);
        IBaseResource created = resourceService.create(resource);

        String responseJson = fhirContext.newJsonParser().encodeResourceToString(created);
        String resourceId = ((Resource) created).getIdElement().getIdPart();

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .header("Location", "/fhir/" + resourceType + "/" + resourceId)
                .header("ETag", "W/\"1\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(responseJson);
    }

    @GetMapping(value = "/{resourceType}/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Read a FHIR resource", description = "Retrieves a specific FHIR resource by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Resource found"),
            @ApiResponse(responseCode = "404", description = "Resource not found"),
            @ApiResponse(responseCode = "410", description = "Resource deleted")
    })
    public ResponseEntity<String> read(
            @Parameter(description = "FHIR resource type")
            @PathVariable String resourceType,
            @Parameter(description = "Resource ID")
            @PathVariable String id) {

        logger.debug("Reading {} with id: {}", resourceType, id);

        IBaseResource resource = resourceService.read(resourceType, id);
        String versionId = ((Resource) resource).getMeta().getVersionId();

        return ResponseEntity.ok()
                .header("ETag", "W/\"" + versionId + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(resource));
    }

    @GetMapping(value = "/{resourceType}/{id}/_history/{versionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Read a specific version of a resource", description = "Retrieves a specific version of a FHIR resource")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Version found"),
            @ApiResponse(responseCode = "404", description = "Version not found")
    })
    public ResponseEntity<String> vread(
            @PathVariable String resourceType,
            @PathVariable String id,
            @PathVariable String versionId) {

        logger.debug("Reading {} with id: {} version: {}", resourceType, id, versionId);

        IBaseResource resource = resourceService.vread(resourceType, id, versionId);

        return ResponseEntity.ok()
                .header("ETag", "W/\"" + versionId + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(resource));
    }

    @PutMapping(value = "/{resourceType}/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update a FHIR resource", description = "Updates an existing FHIR resource or creates it if it doesn't exist")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Resource updated"),
            @ApiResponse(responseCode = "201", description = "Resource created"),
            @ApiResponse(responseCode = "400", description = "Invalid resource format"),
            @ApiResponse(responseCode = "422", description = "Validation error")
    })
    public ResponseEntity<String> update(
            @PathVariable String resourceType,
            @PathVariable String id,
            @RequestBody String resourceJson) {

        logger.info("Updating {} with id: {}", resourceType, id);

        boolean exists = resourceService.resourceExists(resourceType, id);

        IBaseResource resource = fhirContext.newJsonParser().parseResource(resourceJson);
        IBaseResource updated = resourceService.update(resourceType, id, resource);

        String responseJson = fhirContext.newJsonParser().encodeResourceToString(updated);
        String versionId = ((Resource) updated).getMeta().getVersionId();

        return ResponseEntity
                .status(exists ? HttpStatus.OK : HttpStatus.CREATED)
                .header("ETag", "W/\"" + versionId + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(responseJson);
    }

    @DeleteMapping(value = "/{resourceType}/{id}")
    @Operation(summary = "Delete a FHIR resource", description = "Deletes a specific FHIR resource")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Resource deleted"),
            @ApiResponse(responseCode = "404", description = "Resource not found")
    })
    public ResponseEntity<Void> delete(
            @PathVariable String resourceType,
            @PathVariable String id) {

        logger.info("Deleting {} with id: {}", resourceType, id);

        resourceService.delete(resourceType, id);

        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/{resourceType}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Search FHIR resources", description = "Search for resources of a specific type with cursor-based pagination for O(1) performance")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Search results returned as Bundle with cursor links")
    })
    public ResponseEntity<String> search(
            @PathVariable String resourceType,
            @Parameter(description = "Cursor for pagination (ID from previous page's next link)")
            @RequestParam(value = "_cursor", required = false) String cursor,
            @Parameter(description = "Number of results per page (default: 20, max: 100)")
            @RequestParam(value = "_count", defaultValue = "20") int count,
            HttpServletRequest request) {

        logger.debug("Searching {} resources with cursor: {}", resourceType, cursor);

        Map<String, String> searchParams = extractSearchParams(request);

        // Remove pagination params from search params
        searchParams.remove("_cursor");
        searchParams.remove("_count");

        // Use cursor-based pagination
        Bundle bundle = searchWithCursorPagination(resourceType, searchParams, cursor, count);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(bundle));
    }

    @GetMapping(value = "/{resourceType}/{id}/_history", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get resource history", description = "Retrieves the history of a specific resource")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "History bundle returned")
    })
    public ResponseEntity<String> history(
            @PathVariable String resourceType,
            @PathVariable String id,
            @RequestParam(value = "_page", defaultValue = "0") int page,
            @RequestParam(value = "_count", defaultValue = "20") int count) {

        logger.debug("Getting history for {} with id: {}", resourceType, id);

        Bundle bundle = resourceService.history(resourceType, id, page, count);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(bundle));
    }

    @GetMapping(value = "/{resourceType}/_history", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get type-level history", description = "Retrieves the history of all resources of a type")
    public ResponseEntity<String> typeHistory(
            @PathVariable String resourceType,
            @RequestParam(value = "_page", defaultValue = "0") int page,
            @RequestParam(value = "_count", defaultValue = "20") int count) {

        logger.debug("Getting type history for {}", resourceType);

        Bundle bundle = resourceService.typeHistory(resourceType, page, count);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(bundle));
    }

    private Map<String, String> extractSearchParams(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        Enumeration<String> paramNames = request.getParameterNames();

        while (paramNames.hasMoreElements()) {
            String name = paramNames.nextElement();
            String value = request.getParameter(name);
            params.put(name, value);
        }

        return params;
    }

    /**
     * Perform search with cursor-based pagination.
     * Uses MongoDB _id for O(1) pagination performance regardless of result set size.
     */
    private Bundle searchWithCursorPagination(String resourceType, Map<String, String> searchParams,
                                               String cursor, int count) {
        // Limit count to reasonable maximum
        int effectiveCount = Math.min(Math.max(count, 1), 100);

        CursorPage<FhirResourceDocument> cursorPage;

        if (searchParams.isEmpty()) {
            // No search params - use direct cursor pagination
            cursorPage = resourceService.searchWithCursor(resourceType, cursor, effectiveCount);
        } else {
            // With search params - use search service with cursor
            cursorPage = searchService.searchWithCursor(resourceType, searchParams, cursor, effectiveCount);
        }

        return createCursorBundle(cursorPage, resourceType, cursor, effectiveCount);
    }

    /**
     * Create a FHIR Bundle with cursor-based pagination links.
     */
    private Bundle createCursorBundle(CursorPage<FhirResourceDocument> cursorPage, String resourceType,
                                       String currentCursor, int count) {
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.SEARCHSET);

        // Add entries
        for (FhirResourceDocument doc : cursorPage.getContent()) {
            Bundle.BundleEntryComponent entry = bundle.addEntry();

            String json = getResourceJson(doc);
            IBaseResource resource = fhirContext.newJsonParser().parseResource(json);
            entry.setResource((Resource) resource);
            entry.setFullUrl(baseUrl + "/" + resourceType + "/" + doc.getResourceId());

            Bundle.BundleEntrySearchComponent search = new Bundle.BundleEntrySearchComponent();
            search.setMode(Bundle.SearchEntryMode.MATCH);
            entry.setSearch(search);
        }

        // Set total if available
        if (cursorPage.getEstimatedTotal() != null) {
            bundle.setTotal(cursorPage.getEstimatedTotal().intValue());
        }

        // Add self link
        String selfUrl = baseUrl + "/" + resourceType + "?_count=" + count;
        if (currentCursor != null && !currentCursor.isEmpty()) {
            selfUrl += "&_cursor=" + currentCursor;
        }
        bundle.addLink().setRelation("self").setUrl(selfUrl);

        // Add next link if more results available
        if (cursorPage.isHasNext() && cursorPage.getNextCursor() != null) {
            String nextUrl = baseUrl + "/" + resourceType + "?_count=" + count + "&_cursor=" + cursorPage.getNextCursor();
            bundle.addLink().setRelation("next").setUrl(nextUrl);
        }

        // Add previous link if available
        if (cursorPage.isHasPrevious() && cursorPage.getPreviousCursor() != null) {
            String prevUrl = baseUrl + "/" + resourceType + "?_count=" + count + "&_cursor=" + cursorPage.getPreviousCursor();
            bundle.addLink().setRelation("previous").setUrl(prevUrl);
        }

        // Add first link (no cursor = first page)
        bundle.addLink().setRelation("first").setUrl(baseUrl + "/" + resourceType + "?_count=" + count);

        return bundle;
    }

    /**
     * Get resource JSON, handling compression if needed.
     */
    private String getResourceJson(FhirResourceDocument doc) {
        if (doc.getIsCompressed() != null && doc.getIsCompressed() && doc.getCompressedJson() != null) {
            return CompressionUtil.decompress(doc.getCompressedJson());
        }
        return doc.getResourceJson();
    }
}
