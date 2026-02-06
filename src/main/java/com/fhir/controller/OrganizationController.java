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
import org.hl7.fhir.r4.model.Organization;
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
@RequestMapping("/api/organizations")
@Tag(name = "Organization Resource", description = "Dedicated Organization resource operations")
public class OrganizationController {

    private static final Logger logger = LoggerFactory.getLogger(OrganizationController.class);
    private static final String RESOURCE_TYPE = "Organization";

    @Value("${fhir.server.base-url:http://localhost:8080}")
    private String baseUrl;

    @Autowired
    private FhirResourceService resourceService;

    @Autowired
    private FhirSearchService searchService;

    @Autowired
    private FhirContext fhirContext;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create an Organization", description = "Creates a new Organization resource")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Organization created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid Organization format")
    })
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
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Organization found"),
            @ApiResponse(responseCode = "404", description = "Organization not found")
    })
    public ResponseEntity<String> read(@PathVariable String id) {
        logger.debug("Reading Organization with id: {}", id);

        Organization organization = resourceService.read(RESOURCE_TYPE, id);

        return ResponseEntity.ok()
                .header("ETag", "W/\"" + organization.getMeta().getVersionId() + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(organization));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Search Organizations", description = "Search Organizations with cursor-based pagination for O(1) performance")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Search results returned as Bundle with cursor links")
    })
    public ResponseEntity<String> search(
            @Parameter(description = "Cursor for pagination (ID from previous page's next link)")
            @RequestParam(value = "_cursor", required = false) String cursor,
            @Parameter(description = "Number of results per page (default: 20, max: 100)")
            @RequestParam(value = "_count", defaultValue = "20") int count,
            @Parameter(description = "Organization name") @RequestParam(required = false) String name,
            @Parameter(description = "Organization type") @RequestParam(required = false) String type,
            @Parameter(description = "Identifier") @RequestParam(required = false) String identifier,
            @Parameter(description = "Address") @RequestParam(required = false) String address,
            @Parameter(description = "Address city") @RequestParam(value = "address-city", required = false) String addressCity,
            @Parameter(description = "Address state") @RequestParam(value = "address-state", required = false) String addressState,
            @Parameter(description = "Part of organization") @RequestParam(required = false) String partof,
            @Parameter(description = "Active status") @RequestParam(required = false) String active) {

        logger.debug("Searching Organizations with cursor: {}", cursor);

        // Limit count to reasonable maximum
        int effectiveCount = Math.min(Math.max(count, 1), 100);

        Map<String, String> searchParams = new HashMap<>();
        if (name != null) searchParams.put("name", name);
        if (type != null) searchParams.put("type", type);
        if (identifier != null) searchParams.put("identifier", identifier);
        if (address != null) searchParams.put("address", address);
        if (addressCity != null) searchParams.put("address-city", addressCity);
        if (addressState != null) searchParams.put("address-state", addressState);
        if (partof != null) searchParams.put("partof", partof);
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
            entry.setFullUrl(baseUrl + "/api/organizations/" + doc.getResourceId());

            Bundle.BundleEntrySearchComponent search = new Bundle.BundleEntrySearchComponent();
            search.setMode(Bundle.SearchEntryMode.MATCH);
            entry.setSearch(search);
        }

        // Set total if available
        if (cursorPage.getEstimatedTotal() != null) {
            bundle.setTotal(cursorPage.getEstimatedTotal().intValue());
        }

        // Add self link
        String selfUrl = baseUrl + "/api/organizations?_count=" + count;
        if (currentCursor != null && !currentCursor.isEmpty()) {
            selfUrl += "&_cursor=" + currentCursor;
        }
        bundle.addLink().setRelation("self").setUrl(selfUrl);

        // Add next link if more results available
        if (cursorPage.isHasNext() && cursorPage.getNextCursor() != null) {
            String nextUrl = baseUrl + "/api/organizations?_count=" + count + "&_cursor=" + cursorPage.getNextCursor();
            bundle.addLink().setRelation("next").setUrl(nextUrl);
        }

        // Add first link (no cursor = first page)
        bundle.addLink().setRelation("first").setUrl(baseUrl + "/api/organizations?_count=" + count);

        return bundle;
    }

    private String getResourceJson(FhirResourceDocument doc) {
        if (doc.getIsCompressed() != null && doc.getIsCompressed() && doc.getCompressedJson() != null) {
            return CompressionUtil.decompress(doc.getCompressedJson());
        }
        return doc.getResourceJson();
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update an Organization", description = "Updates an existing Organization")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Organization updated"),
            @ApiResponse(responseCode = "201", description = "Organization created"),
            @ApiResponse(responseCode = "400", description = "Invalid Organization format")
    })
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
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Organization deleted"),
            @ApiResponse(responseCode = "404", description = "Organization not found")
    })
    public ResponseEntity<Void> delete(@PathVariable String id) {
        logger.info("Deleting Organization with id: {}", id);

        resourceService.delete(RESOURCE_TYPE, id);

        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/{id}/_history", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get Organization history", description = "Retrieves the version history of an Organization")
    public ResponseEntity<String> getOrganizationHistory(
            @PathVariable String id,
            @RequestParam(value = "_page", defaultValue = "0") int page,
            @RequestParam(value = "_count", defaultValue = "20") int count) {

        logger.debug("Getting history for Organization with id: {}", id);

        Bundle bundle = resourceService.history(RESOURCE_TYPE, id, page, count);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(bundle));
    }

    @GetMapping(value = "/{id}/_history/{versionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Read specific Organization version", description = "Retrieves a specific version of an Organization")
    public ResponseEntity<String> getOrganizationVersion(
            @PathVariable String id,
            @PathVariable String versionId) {

        logger.debug("Reading Organization with id: {} version: {}", id, versionId);

        Organization organization = resourceService.vread(RESOURCE_TYPE, id, versionId);

        return ResponseEntity.ok()
                .header("ETag", "W/\"" + versionId + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(organization));
    }
}
