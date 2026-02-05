package com.fhir.e2e;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-End test simulating a complete patient healthcare journey.
 * Tests the full workflow from patient registration through discharge.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Patient Journey E2E Tests")
class PatientJourneyE2ETest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:6.0");

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
        registry.add("spring.cache.type", () -> "simple");
        registry.add("spring.data.redis.repositories.enabled", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FhirContext fhirContext;

    // Resource IDs created during the test
    private static String organizationId;
    private static String practitionerId;
    private static String patientId;
    private static String encounterId;
    private static String conditionId;
    private static String observationId;
    private static String medicationRequestId;
    private static String procedureId;

    @Test
    @Order(1)
    @DisplayName("Step 1: Create Organization (Hospital)")
    void step1_createOrganization() throws Exception {
        String organizationJson = """
            {
                "resourceType": "Organization",
                "name": "E2E Test Hospital",
                "active": true,
                "type": [{
                    "coding": [{
                        "system": "http://terminology.hl7.org/CodeSystem/organization-type",
                        "code": "prov",
                        "display": "Healthcare Provider"
                    }]
                }],
                "telecom": [{"system": "phone", "value": "555-1000"}],
                "address": [{
                    "line": ["123 Hospital Ave"],
                    "city": "Boston",
                    "state": "MA",
                    "postalCode": "02115",
                    "country": "USA"
                }]
            }
            """;

        MvcResult result = mockMvc.perform(post("/fhir/Organization")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(organizationJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resourceType").value("Organization"))
                .andExpect(jsonPath("$.name").value("E2E Test Hospital"))
                .andReturn();

        Organization org = fhirContext.newJsonParser().parseResource(Organization.class,
                result.getResponse().getContentAsString());
        organizationId = org.getIdElement().getIdPart();

        assertNotNull(organizationId);
        System.out.println("Created Organization: " + organizationId);
    }

    @Test
    @Order(2)
    @DisplayName("Step 2: Create Practitioner (Doctor)")
    void step2_createPractitioner() throws Exception {
        String practitionerJson = """
            {
                "resourceType": "Practitioner",
                "active": true,
                "name": [{
                    "family": "Wilson",
                    "given": ["Sarah"],
                    "prefix": ["Dr."]
                }],
                "telecom": [
                    {"system": "phone", "value": "555-2000"},
                    {"system": "email", "value": "dr.wilson@hospital.org"}
                ],
                "qualification": [{
                    "code": {
                        "coding": [{
                            "system": "http://terminology.hl7.org/CodeSystem/v2-0360",
                            "code": "MD",
                            "display": "Doctor of Medicine"
                        }]
                    }
                }]
            }
            """;

        MvcResult result = mockMvc.perform(post("/fhir/Practitioner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(practitionerJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resourceType").value("Practitioner"))
                .andExpect(jsonPath("$.name[0].family").value("Wilson"))
                .andReturn();

        Practitioner prac = fhirContext.newJsonParser().parseResource(Practitioner.class,
                result.getResponse().getContentAsString());
        practitionerId = prac.getIdElement().getIdPart();

        assertNotNull(practitionerId);
        System.out.println("Created Practitioner: " + practitionerId);
    }

    @Test
    @Order(3)
    @DisplayName("Step 3: Register Patient")
    void step3_createPatient() throws Exception {
        assertNotNull(organizationId, "Organization must be created first");

        String patientJson = """
            {
                "resourceType": "Patient",
                "active": true,
                "name": [{
                    "use": "official",
                    "family": "Johnson",
                    "given": ["Emily", "Rose"]
                }],
                "telecom": [
                    {"system": "phone", "value": "555-3000", "use": "home"},
                    {"system": "email", "value": "emily.johnson@email.com"}
                ],
                "gender": "female",
                "birthDate": "1985-03-15",
                "address": [{
                    "use": "home",
                    "line": ["456 Patient Street"],
                    "city": "Cambridge",
                    "state": "MA",
                    "postalCode": "02139",
                    "country": "USA"
                }],
                "maritalStatus": {
                    "coding": [{
                        "system": "http://terminology.hl7.org/CodeSystem/v3-MaritalStatus",
                        "code": "M",
                        "display": "Married"
                    }]
                },
                "managingOrganization": {"reference": "Organization/%s"}
            }
            """.formatted(organizationId);

        MvcResult result = mockMvc.perform(post("/fhir/Patient")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patientJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resourceType").value("Patient"))
                .andExpect(jsonPath("$.name[0].family").value("Johnson"))
                .andExpect(jsonPath("$.gender").value("female"))
                .andReturn();

        Patient patient = fhirContext.newJsonParser().parseResource(Patient.class,
                result.getResponse().getContentAsString());
        patientId = patient.getIdElement().getIdPart();

        assertNotNull(patientId);
        System.out.println("Created Patient: " + patientId);
    }

    @Test
    @Order(4)
    @DisplayName("Step 4: Create Encounter (Patient Visit)")
    void step4_createEncounter() throws Exception {
        assertNotNull(patientId, "Patient must be created first");
        assertNotNull(practitionerId, "Practitioner must be created first");
        assertNotNull(organizationId, "Organization must be created first");

        String encounterJson = """
            {
                "resourceType": "Encounter",
                "status": "in-progress",
                "class": {
                    "system": "http://terminology.hl7.org/CodeSystem/v3-ActCode",
                    "code": "AMB",
                    "display": "ambulatory"
                },
                "type": [{
                    "coding": [{
                        "system": "http://snomed.info/sct",
                        "code": "270427003",
                        "display": "Patient-initiated encounter"
                    }]
                }],
                "subject": {"reference": "Patient/%s"},
                "participant": [{
                    "type": [{
                        "coding": [{
                            "system": "http://terminology.hl7.org/CodeSystem/v3-ParticipationType",
                            "code": "PPRF",
                            "display": "primary performer"
                        }]
                    }],
                    "individual": {"reference": "Practitioner/%s"}
                }],
                "period": {"start": "2024-01-15T09:00:00Z"},
                "reasonCode": [{
                    "coding": [{
                        "system": "http://snomed.info/sct",
                        "code": "386661006",
                        "display": "Fever"
                    }]
                }],
                "serviceProvider": {"reference": "Organization/%s"}
            }
            """.formatted(patientId, practitionerId, organizationId);

        MvcResult result = mockMvc.perform(post("/fhir/Encounter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(encounterJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resourceType").value("Encounter"))
                .andExpect(jsonPath("$.status").value("in-progress"))
                .andReturn();

        Encounter encounter = fhirContext.newJsonParser().parseResource(Encounter.class,
                result.getResponse().getContentAsString());
        encounterId = encounter.getIdElement().getIdPart();

        assertNotNull(encounterId);
        System.out.println("Created Encounter: " + encounterId);
    }

    @Test
    @Order(5)
    @DisplayName("Step 5: Record Vital Signs (Observation)")
    void step5_createObservation() throws Exception {
        assertNotNull(patientId, "Patient must be created first");
        assertNotNull(encounterId, "Encounter must be created first");

        String observationJson = """
            {
                "resourceType": "Observation",
                "status": "final",
                "category": [{
                    "coding": [{
                        "system": "http://terminology.hl7.org/CodeSystem/observation-category",
                        "code": "vital-signs",
                        "display": "Vital Signs"
                    }]
                }],
                "code": {
                    "coding": [{
                        "system": "http://loinc.org",
                        "code": "8310-5",
                        "display": "Body temperature"
                    }]
                },
                "subject": {"reference": "Patient/%s"},
                "encounter": {"reference": "Encounter/%s"},
                "effectiveDateTime": "2024-01-15T09:15:00Z",
                "valueQuantity": {
                    "value": 38.5,
                    "unit": "degrees C",
                    "system": "http://unitsofmeasure.org",
                    "code": "Cel"
                },
                "interpretation": [{
                    "coding": [{
                        "system": "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation",
                        "code": "H",
                        "display": "High"
                    }]
                }]
            }
            """.formatted(patientId, encounterId);

        MvcResult result = mockMvc.perform(post("/fhir/Observation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(observationJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resourceType").value("Observation"))
                .andExpect(jsonPath("$.status").value("final"))
                .andExpect(jsonPath("$.valueQuantity.value").value(38.5))
                .andReturn();

        Observation obs = fhirContext.newJsonParser().parseResource(Observation.class,
                result.getResponse().getContentAsString());
        observationId = obs.getIdElement().getIdPart();

        assertNotNull(observationId);
        System.out.println("Created Observation: " + observationId);
    }

    @Test
    @Order(6)
    @DisplayName("Step 6: Diagnose Condition")
    void step6_createCondition() throws Exception {
        assertNotNull(patientId, "Patient must be created first");
        assertNotNull(encounterId, "Encounter must be created first");
        assertNotNull(practitionerId, "Practitioner must be created first");

        String conditionJson = """
            {
                "resourceType": "Condition",
                "clinicalStatus": {
                    "coding": [{
                        "system": "http://terminology.hl7.org/CodeSystem/condition-clinical",
                        "code": "active",
                        "display": "Active"
                    }]
                },
                "verificationStatus": {
                    "coding": [{
                        "system": "http://terminology.hl7.org/CodeSystem/condition-ver-status",
                        "code": "confirmed",
                        "display": "Confirmed"
                    }]
                },
                "category": [{
                    "coding": [{
                        "system": "http://terminology.hl7.org/CodeSystem/condition-category",
                        "code": "encounter-diagnosis",
                        "display": "Encounter Diagnosis"
                    }]
                }],
                "severity": {
                    "coding": [{
                        "system": "http://snomed.info/sct",
                        "code": "6736007",
                        "display": "Moderate"
                    }]
                },
                "code": {
                    "coding": [{
                        "system": "http://snomed.info/sct",
                        "code": "386661006",
                        "display": "Fever"
                    }],
                    "text": "Acute viral infection with fever"
                },
                "subject": {"reference": "Patient/%s"},
                "encounter": {"reference": "Encounter/%s"},
                "onsetDateTime": "2024-01-14",
                "asserter": {"reference": "Practitioner/%s"}
            }
            """.formatted(patientId, encounterId, practitionerId);

        MvcResult result = mockMvc.perform(post("/fhir/Condition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conditionJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resourceType").value("Condition"))
                .andReturn();

        Condition condition = fhirContext.newJsonParser().parseResource(Condition.class,
                result.getResponse().getContentAsString());
        conditionId = condition.getIdElement().getIdPart();

        assertNotNull(conditionId);
        System.out.println("Created Condition: " + conditionId);
    }

    @Test
    @Order(7)
    @DisplayName("Step 7: Prescribe Medication")
    void step7_createMedicationRequest() throws Exception {
        assertNotNull(patientId, "Patient must be created first");
        assertNotNull(encounterId, "Encounter must be created first");
        assertNotNull(practitionerId, "Practitioner must be created first");
        assertNotNull(conditionId, "Condition must be created first");

        String medicationRequestJson = """
            {
                "resourceType": "MedicationRequest",
                "status": "active",
                "intent": "order",
                "medicationCodeableConcept": {
                    "coding": [{
                        "system": "http://www.nlm.nih.gov/research/umls/rxnorm",
                        "code": "161",
                        "display": "Acetaminophen"
                    }]
                },
                "subject": {"reference": "Patient/%s"},
                "encounter": {"reference": "Encounter/%s"},
                "authoredOn": "2024-01-15T10:00:00Z",
                "requester": {"reference": "Practitioner/%s"},
                "reasonReference": [{"reference": "Condition/%s"}],
                "dosageInstruction": [{
                    "text": "Take 500mg every 6 hours as needed for fever",
                    "timing": {
                        "repeat": {
                            "frequency": 4,
                            "period": 1,
                            "periodUnit": "d"
                        }
                    },
                    "doseAndRate": [{
                        "doseQuantity": {
                            "value": 500,
                            "unit": "mg",
                            "system": "http://unitsofmeasure.org",
                            "code": "mg"
                        }
                    }]
                }]
            }
            """.formatted(patientId, encounterId, practitionerId, conditionId);

        MvcResult result = mockMvc.perform(post("/fhir/MedicationRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(medicationRequestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resourceType").value("MedicationRequest"))
                .andExpect(jsonPath("$.status").value("active"))
                .andReturn();

        MedicationRequest medReq = fhirContext.newJsonParser().parseResource(MedicationRequest.class,
                result.getResponse().getContentAsString());
        medicationRequestId = medReq.getIdElement().getIdPart();

        assertNotNull(medicationRequestId);
        System.out.println("Created MedicationRequest: " + medicationRequestId);
    }

    @Test
    @Order(8)
    @DisplayName("Step 8: Perform Procedure (Blood Test)")
    void step8_createProcedure() throws Exception {
        assertNotNull(patientId, "Patient must be created first");
        assertNotNull(encounterId, "Encounter must be created first");
        assertNotNull(practitionerId, "Practitioner must be created first");

        String procedureJson = """
            {
                "resourceType": "Procedure",
                "status": "completed",
                "category": {
                    "coding": [{
                        "system": "http://snomed.info/sct",
                        "code": "103693007",
                        "display": "Diagnostic procedure"
                    }]
                },
                "code": {
                    "coding": [{
                        "system": "http://snomed.info/sct",
                        "code": "396550006",
                        "display": "Blood test"
                    }]
                },
                "subject": {"reference": "Patient/%s"},
                "encounter": {"reference": "Encounter/%s"},
                "performedDateTime": "2024-01-15T10:30:00Z",
                "performer": [{
                    "actor": {"reference": "Practitioner/%s"}
                }]
            }
            """.formatted(patientId, encounterId, practitionerId);

        MvcResult result = mockMvc.perform(post("/fhir/Procedure")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(procedureJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resourceType").value("Procedure"))
                .andExpect(jsonPath("$.status").value("completed"))
                .andReturn();

        Procedure procedure = fhirContext.newJsonParser().parseResource(Procedure.class,
                result.getResponse().getContentAsString());
        procedureId = procedure.getIdElement().getIdPart();

        assertNotNull(procedureId);
        System.out.println("Created Procedure: " + procedureId);
    }

    @Test
    @Order(9)
    @DisplayName("Step 9: Complete Encounter (Discharge)")
    void step9_updateEncounterToFinished() throws Exception {
        assertNotNull(encounterId, "Encounter must be created first");

        // First, read the current encounter
        MvcResult readResult = mockMvc.perform(get("/fhir/Encounter/" + encounterId)
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn();

        String encounterJson = readResult.getResponse().getContentAsString();
        Encounter encounter = fhirContext.newJsonParser().parseResource(Encounter.class, encounterJson);

        // Update the status to finished
        encounter.setStatus(Encounter.EncounterStatus.FINISHED);
        encounter.getPeriod().setEnd(new Date());

        String updatedEncounterJson = fhirContext.newJsonParser().encodeResourceToString(encounter);

        mockMvc.perform(put("/fhir/Encounter/" + encounterId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatedEncounterJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("finished"));

        System.out.println("Updated Encounter to finished: " + encounterId);
    }

    @Test
    @Order(10)
    @DisplayName("Step 10: Verify Complete Patient Record")
    void step10_verifyCompletePatientRecord() throws Exception {
        // Verify Patient
        mockMvc.perform(get("/fhir/Patient/" + patientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceType").value("Patient"));

        // Verify Encounter is finished
        mockMvc.perform(get("/fhir/Encounter/" + encounterId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("finished"));

        // Verify Condition exists
        mockMvc.perform(get("/fhir/Condition/" + conditionId))
                .andExpect(status().isOk());

        // Verify MedicationRequest exists
        mockMvc.perform(get("/fhir/MedicationRequest/" + medicationRequestId))
                .andExpect(status().isOk());

        // Verify Procedure exists
        mockMvc.perform(get("/fhir/Procedure/" + procedureId))
                .andExpect(status().isOk());

        System.out.println("Patient Journey Complete!");
        System.out.println("  Organization: " + organizationId);
        System.out.println("  Practitioner: " + practitionerId);
        System.out.println("  Patient: " + patientId);
        System.out.println("  Encounter: " + encounterId);
        System.out.println("  Observation: " + observationId);
        System.out.println("  Condition: " + conditionId);
        System.out.println("  MedicationRequest: " + medicationRequestId);
        System.out.println("  Procedure: " + procedureId);
    }

    @Test
    @Order(11)
    @DisplayName("Step 11: Search for Patient's Observations")
    void step11_searchPatientObservations() throws Exception {
        mockMvc.perform(get("/fhir/Observation")
                        .param("patient", "Patient/" + patientId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceType").value("Bundle"))
                .andExpect(jsonPath("$.type").value("searchset"));
    }

    @Test
    @Order(12)
    @DisplayName("Step 12: Search for Patient's Conditions")
    void step12_searchPatientConditions() throws Exception {
        mockMvc.perform(get("/fhir/Condition")
                        .param("patient", "Patient/" + patientId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceType").value("Bundle"));
    }

    @Test
    @Order(13)
    @DisplayName("Step 13: Get Patient History")
    void step13_getPatientHistory() throws Exception {
        mockMvc.perform(get("/fhir/Patient/" + patientId + "/_history")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceType").value("Bundle"))
                .andExpect(jsonPath("$.type").value("history"));
    }
}
