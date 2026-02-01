package com.fhir.controller;

import ca.uhn.fhir.context.FhirContext;
import com.fhir.service.FhirResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    @Operation(summary = "System-level search", description = "Search across all resource types")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Search results returned as Bundle")
    })
    public ResponseEntity<String> systemSearch(
            @RequestParam(value = "_page", defaultValue = "0") int page,
            @RequestParam(value = "_count", defaultValue = "20") int count,
            HttpServletRequest request) {

        logger.debug("Performing system-level search");

        Map<String, String> searchParams = extractSearchParams(request);
        Bundle bundle = resourceService.searchAll(searchParams, page, count);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(bundle));
    }

    @GetMapping(value = "/_history", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "System-level history", description = "Get history of all resources on the server")
    public ResponseEntity<String> systemHistory(
            @RequestParam(value = "_page", defaultValue = "0") int page,
            @RequestParam(value = "_count", defaultValue = "20") int count) {

        logger.debug("Getting system-level history");

        Bundle bundle = resourceService.searchAll(new HashMap<>(), page, count);
        bundle.setType(Bundle.BundleType.HISTORY);

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
}
