package com.fhir.controller;

import ca.uhn.fhir.context.FhirContext;
import com.fhir.service.FhirResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;

@RestController
@RequestMapping("/api/patients")
@Tag(name = "Patient Resource", description = "Dedicated Patient resource operations")
public class PatientController {

    private static final Logger logger = LoggerFactory.getLogger(PatientController.class);
    private static final String RESOURCE_TYPE = "Patient";

    @Autowired
    private FhirResourceService resourceService;

    @Autowired
    private FhirContext fhirContext;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a Patient", description = "Creates a new Patient resource")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Patient created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid Patient format")
    })
    public ResponseEntity<String> createPatient(@RequestBody String patientJson) {
        logger.info("Creating new Patient");

        Patient patient = fhirContext.newJsonParser().parseResource(Patient.class, patientJson);
        Patient created = resourceService.create(patient);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .header("Location", "/api/patients/" + created.getIdElement().getIdPart())
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(created));
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Read a Patient", description = "Retrieves a Patient by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Patient found"),
            @ApiResponse(responseCode = "404", description = "Patient not found")
    })
    public ResponseEntity<String> getPatient(
            @Parameter(description = "Patient ID")
            @PathVariable String id) {

        logger.debug("Reading Patient with id: {}", id);

        Patient patient = resourceService.read(RESOURCE_TYPE, id);

        return ResponseEntity.ok()
                .header("ETag", "W/\"" + patient.getMeta().getVersionId() + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(patient));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Search Patients", description = "Search all Patients")
    public ResponseEntity<String> getAllPatients(
            @RequestParam(value = "_page", defaultValue = "0") int page,
            @RequestParam(value = "_count", defaultValue = "20") int count) {

        logger.debug("Searching all Patients");

        Bundle bundle = resourceService.search(RESOURCE_TYPE, new HashMap<>(), page, count);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(bundle));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update a Patient", description = "Updates an existing Patient")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Patient updated"),
            @ApiResponse(responseCode = "201", description = "Patient created"),
            @ApiResponse(responseCode = "400", description = "Invalid Patient format")
    })
    public ResponseEntity<String> updatePatient(
            @PathVariable String id,
            @RequestBody String patientJson) {

        logger.info("Updating Patient with id: {}", id);

        boolean exists = resourceService.resourceExists(RESOURCE_TYPE, id);
        Patient patient = fhirContext.newJsonParser().parseResource(Patient.class, patientJson);
        Patient updated = resourceService.update(RESOURCE_TYPE, id, patient);

        return ResponseEntity
                .status(exists ? HttpStatus.OK : HttpStatus.CREATED)
                .header("ETag", "W/\"" + updated.getMeta().getVersionId() + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(updated));
    }

    @DeleteMapping(value = "/{id}")
    @Operation(summary = "Delete a Patient", description = "Deletes a Patient by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Patient deleted"),
            @ApiResponse(responseCode = "404", description = "Patient not found")
    })
    public ResponseEntity<Void> deletePatient(@PathVariable String id) {
        logger.info("Deleting Patient with id: {}", id);

        resourceService.delete(RESOURCE_TYPE, id);

        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/{id}/_history", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get Patient history", description = "Retrieves the version history of a Patient")
    public ResponseEntity<String> getPatientHistory(
            @PathVariable String id,
            @RequestParam(value = "_page", defaultValue = "0") int page,
            @RequestParam(value = "_count", defaultValue = "20") int count) {

        logger.debug("Getting history for Patient with id: {}", id);

        Bundle bundle = resourceService.history(RESOURCE_TYPE, id, page, count);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(bundle));
    }

    @GetMapping(value = "/{id}/_history/{versionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Read specific Patient version", description = "Retrieves a specific version of a Patient")
    public ResponseEntity<String> getPatientVersion(
            @PathVariable String id,
            @PathVariable String versionId) {

        logger.debug("Reading Patient with id: {} version: {}", id, versionId);

        Patient patient = resourceService.vread(RESOURCE_TYPE, id, versionId);

        return ResponseEntity.ok()
                .header("ETag", "W/\"" + versionId + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(fhirContext.newJsonParser().encodeResourceToString(patient));
    }
}
