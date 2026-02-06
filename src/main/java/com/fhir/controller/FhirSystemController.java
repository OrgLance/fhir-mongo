package com.fhir.controller;

import ca.uhn.fhir.context.FhirContext;
import com.fhir.service.FhirResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/fhir")
@Tag(name = "FHIR System Operations", description = "System-level FHIR operations")
public class FhirSystemController {

    private static final Logger logger = LoggerFactory.getLogger(FhirSystemController.class);

    @Value("${fhir.server.base-url:http://localhost:8080/fhir}")
    private String baseUrl;

    @Autowired
    private FhirResourceService resourceService;

    @Autowired
    private FhirContext fhirContext;

    @GetMapping(value = "/metadata", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get CapabilityStatement", description = "Returns the server's capability statement (conformance resource)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "CapabilityStatement returned")
    })
    public ResponseEntity<String> getMetadata() {
        logger.debug("Getting server metadata (CapabilityStatement)");

        CapabilityStatement cs = resourceService.getCapabilityStatement();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(cs));
    }

    @PostMapping(value = "", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Execute transaction/batch", description = "Executes a transaction or batch bundle")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transaction/batch processed"),
            @ApiResponse(responseCode = "400", description = "Invalid bundle format")
    })
    public ResponseEntity<String> transaction(@RequestBody String bundleJson) {
        logger.info("Processing transaction/batch bundle");

        Bundle transactionBundle = fhirContext.newJsonParser().parseResource(Bundle.class, bundleJson);
        Bundle responseBundle = resourceService.transaction(transactionBundle);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(responseBundle));
    }

    @GetMapping(value = "", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "System-level search", description = "Search across all resource types with cursor-based pagination")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Search results returned as Bundle with cursor links")
    })
    public ResponseEntity<String> systemSearch(
            @Parameter(description = "Cursor for pagination (ID from previous page's next link)")
            @RequestParam(value = "_cursor", required = false) String cursor,
            @Parameter(description = "Number of results per page (default: 20, max: 100)")
            @RequestParam(value = "_count", defaultValue = "20") int count,
            HttpServletRequest request) {

        logger.debug("Performing system-level search with cursor: {}", cursor);

        // Limit count to reasonable maximum
        int effectiveCount = Math.min(Math.max(count, 1), 100);

        Map<String, String> searchParams = extractSearchParams(request);
        searchParams.remove("_cursor");
        searchParams.remove("_count");

        Bundle bundle = resourceService.searchAllWithCursor(searchParams, cursor, effectiveCount);

        // Add pagination links
        addSystemSearchLinks(bundle, cursor, effectiveCount);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(bundle));
    }

    @GetMapping(value = "/_history", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "System-level history", description = "Get history of all resources on the server with cursor-based pagination")
    public ResponseEntity<String> systemHistory(
            @Parameter(description = "Cursor for pagination")
            @RequestParam(value = "_cursor", required = false) String cursor,
            @Parameter(description = "Number of results per page")
            @RequestParam(value = "_count", defaultValue = "20") int count) {

        logger.debug("Getting system-level history with cursor: {}", cursor);

        int effectiveCount = Math.min(Math.max(count, 1), 100);

        Bundle bundle = resourceService.searchAllWithCursor(new HashMap<>(), cursor, effectiveCount);
        bundle.setType(Bundle.BundleType.HISTORY);

        // Add pagination links
        addSystemHistoryLinks(bundle, cursor, effectiveCount);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(bundle));
    }

    private void addSystemSearchLinks(Bundle bundle, String cursor, int count) {
        // Add self link
        String selfUrl = baseUrl + "?_count=" + count;
        if (cursor != null && !cursor.isEmpty()) {
            selfUrl += "&_cursor=" + cursor;
        }
        bundle.addLink().setRelation("self").setUrl(selfUrl);

        // Add first link
        bundle.addLink().setRelation("first").setUrl(baseUrl + "?_count=" + count);
    }

    private void addSystemHistoryLinks(Bundle bundle, String cursor, int count) {
        // Add self link
        String selfUrl = baseUrl + "/_history?_count=" + count;
        if (cursor != null && !cursor.isEmpty()) {
            selfUrl += "&_cursor=" + cursor;
        }
        bundle.addLink().setRelation("self").setUrl(selfUrl);

        // Add first link
        bundle.addLink().setRelation("first").setUrl(baseUrl + "/_history?_count=" + count);
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
}
