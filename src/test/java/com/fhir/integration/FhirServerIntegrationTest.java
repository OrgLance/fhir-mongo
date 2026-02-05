package com.fhir.integration;

import ca.uhn.fhir.context.FhirContext;
import com.fhir.model.FhirResourceDocument;
import com.fhir.repository.DynamicFhirResourceRepository;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("FHIR Server Integration Tests")
class FhirServerIntegrationTest {

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

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private DynamicFhirResourceRepository repository;

    private static String createdPatientId;
    private static String createdObservationId;
    private static String createdEncounterId;

    @BeforeEach
    void setUp() {
        // Clean collections before each test class
    }

    @Nested
    @DisplayName("Patient CRUD Operations")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class PatientCrudTests {

        @Test
        @Order(1)
        @DisplayName("Should create a Patient")
        void shouldCreatePatient() throws Exception {
            String patientJson = """
                {
                    "resourceType": "Patient",
                    "name": [{"family": "TestSmith", "given": ["John", "Michael"]}],
                    "gender": "male",
                    "birthDate": "1990-01-15",
                    "telecom": [{"system": "phone", "value": "555-1234"}],
                    "address": [{"city": "Boston", "state": "MA", "country": "USA"}]
                }
                """;

            MvcResult result = mockMvc.perform(post("/fhir/Patient")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(patientJson))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"))
                    .andExpect(header().string("ETag", "W/\"1\""))
                    .andExpect(jsonPath("$.resourceType").value("Patient"))
                    .andExpect(jsonPath("$.name[0].family").value("TestSmith"))
                    .andExpect(jsonPath("$.gender").value("male"))
                    .andReturn();

            String responseJson = result.getResponse().getContentAsString();
            Patient patient = fhirContext.newJsonParser().parseResource(Patient.class, responseJson);
            createdPatientId = patient.getIdElement().getIdPart();

            assertNotNull(createdPatientId);
        }

        @Test
        @Order(2)
        @DisplayName("Should read the created Patient")
        void shouldReadPatient() throws Exception {
            assertNotNull(createdPatientId, "Patient should have been created in previous test");

            mockMvc.perform(get("/fhir/Patient/" + createdPatientId)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resourceType").value("Patient"))
                    .andExpect(jsonPath("$.id").value(createdPatientId))
                    .andExpect(jsonPath("$.name[0].family").value("TestSmith"));
        }

        @Test
        @Order(3)
        @DisplayName("Should update the Patient")
        void shouldUpdatePatient() throws Exception {
            assertNotNull(createdPatientId, "Patient should have been created");

            String updatedPatientJson = """
                {
                    "resourceType": "Patient",
                    "id": "%s",
                    "name": [{"family": "UpdatedSmith", "given": ["John", "Michael"]}],
                    "gender": "male",
                    "birthDate": "1990-01-15",
                    "active": true
                }
                """.formatted(createdPatientId);

            mockMvc.perform(put("/fhir/Patient/" + createdPatientId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updatedPatientJson))
                    .andExpect(status().isOk())
                    .andExpect(header().string("ETag", "W/\"2\""))
                    .andExpect(jsonPath("$.name[0].family").value("UpdatedSmith"));
        }

        @Test
        @Order(4)
        @DisplayName("Should get Patient history")
        void shouldGetPatientHistory() throws Exception {
            assertNotNull(createdPatientId, "Patient should have been created");

            mockMvc.perform(get("/fhir/Patient/" + createdPatientId + "/_history")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resourceType").value("Bundle"))
                    .andExpect(jsonPath("$.type").value("history"));
        }

        @Test
        @Order(5)
        @DisplayName("Should read specific version of Patient")
        void shouldReadSpecificVersion() throws Exception {
            assertNotNull(createdPatientId, "Patient should have been created");

            mockMvc.perform(get("/fhir/Patient/" + createdPatientId + "/_history/1")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resourceType").value("Patient"));
        }
    }

    @Nested
    @DisplayName("Observation CRUD Operations")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class ObservationCrudTests {

        @Test
        @Order(1)
        @DisplayName("Should create an Observation")
        void shouldCreateObservation() throws Exception {
            // First create a patient if not exists
            if (createdPatientId == null) {
                String patientJson = "{\"resourceType\":\"Patient\",\"name\":[{\"family\":\"Temp\"}]}";
                MvcResult patientResult = mockMvc.perform(post("/fhir/Patient")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(patientJson))
                        .andReturn();
                Patient p = fhirContext.newJsonParser().parseResource(Patient.class,
                        patientResult.getResponse().getContentAsString());
                createdPatientId = p.getIdElement().getIdPart();
            }

            String observationJson = """
                {
                    "resourceType": "Observation",
                    "status": "final",
                    "category": [{
                        "coding": [{
                            "system": "http://terminology.hl7.org/CodeSystem/observation-category",
                            "code": "vital-signs"
                        }]
                    }],
                    "code": {
                        "coding": [{
                            "system": "http://loinc.org",
                            "code": "8867-4",
                            "display": "Heart rate"
                        }]
                    },
                    "subject": {"reference": "Patient/%s"},
                    "valueQuantity": {
                        "value": 72,
                        "unit": "beats/minute",
                        "system": "http://unitsofmeasure.org",
                        "code": "/min"
                    }
                }
                """.formatted(createdPatientId);

            MvcResult result = mockMvc.perform(post("/fhir/Observation")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(observationJson))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.resourceType").value("Observation"))
                    .andExpect(jsonPath("$.status").value("final"))
                    .andReturn();

            Observation obs = fhirContext.newJsonParser().parseResource(Observation.class,
                    result.getResponse().getContentAsString());
            createdObservationId = obs.getIdElement().getIdPart();

            assertNotNull(createdObservationId);
        }

        @Test
        @Order(2)
        @DisplayName("Should read the created Observation")
        void shouldReadObservation() throws Exception {
            assertNotNull(createdObservationId, "Observation should have been created");

            mockMvc.perform(get("/fhir/Observation/" + createdObservationId)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resourceType").value("Observation"))
                    .andExpect(jsonPath("$.status").value("final"));
        }
    }

    @Nested
    @DisplayName("Search Operations")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class SearchTests {

        @Test
        @Order(1)
        @DisplayName("Should search all Patients")
        void shouldSearchAllPatients() throws Exception {
            mockMvc.perform(get("/fhir/Patient")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resourceType").value("Bundle"))
                    .andExpect(jsonPath("$.type").value("searchset"));
        }

        @Test
        @Order(2)
        @DisplayName("Should search Patients by name")
        void shouldSearchPatientsByName() throws Exception {
            // Create a patient first
            String patientJson = "{\"resourceType\":\"Patient\",\"name\":[{\"family\":\"SearchableSmith\"}]}";
            mockMvc.perform(post("/fhir/Patient")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(patientJson));

            mockMvc.perform(get("/fhir/Patient")
                            .param("name", "SearchableSmith")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resourceType").value("Bundle"))
                    .andExpect(jsonPath("$.type").value("searchset"));
        }

        @Test
        @Order(3)
        @DisplayName("Should search with pagination")
        void shouldSearchWithPagination() throws Exception {
            mockMvc.perform(get("/fhir/Patient")
                            .param("_page", "0")
                            .param("_count", "5")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resourceType").value("Bundle"));
        }

        @Test
        @Order(4)
        @DisplayName("Should search all Observations")
        void shouldSearchAllObservations() throws Exception {
            mockMvc.perform(get("/fhir/Observation")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resourceType").value("Bundle"))
                    .andExpect(jsonPath("$.type").value("searchset"));
        }
    }

    @Nested
    @DisplayName("Delete Operations")
    class DeleteTests {

        @Test
        @DisplayName("Should delete a resource")
        void shouldDeleteResource() throws Exception {
            // Create a patient to delete
            String patientJson = "{\"resourceType\":\"Patient\",\"name\":[{\"family\":\"ToBeDeleted\"}]}";
            MvcResult result = mockMvc.perform(post("/fhir/Patient")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(patientJson))
                    .andReturn();

            Patient patient = fhirContext.newJsonParser().parseResource(Patient.class,
                    result.getResponse().getContentAsString());
            String patientId = patient.getIdElement().getIdPart();

            // Delete the patient
            mockMvc.perform(delete("/fhir/Patient/" + patientId))
                    .andExpect(status().isNoContent());

            // Verify it's deleted
            mockMvc.perform(get("/fhir/Patient/" + patientId)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should return 404 for non-existent resource")
        void shouldReturn404ForNonExistentResource() throws Exception {
            mockMvc.perform(get("/fhir/Patient/non-existent-id")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should handle invalid JSON gracefully")
        void shouldHandleInvalidJson() throws Exception {
            mockMvc.perform(post("/fhir/Patient")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("invalid json"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Capability Statement Tests")
    class CapabilityStatementTests {

        @Test
        @DisplayName("Should return capability statement")
        void shouldReturnCapabilityStatement() throws Exception {
            mockMvc.perform(get("/fhir/metadata")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resourceType").value("CapabilityStatement"))
                    .andExpect(jsonPath("$.status").value("active"))
                    .andExpect(jsonPath("$.fhirVersion").value("4.0.1"));
        }
    }

    @Nested
    @DisplayName("Dynamic Collection Tests")
    class DynamicCollectionTests {

        @Test
        @DisplayName("Should store different resource types in different collections")
        void shouldStoreDifferentResourceTypesInDifferentCollections() throws Exception {
            // Create a Patient
            String patientJson = "{\"resourceType\":\"Patient\",\"name\":[{\"family\":\"CollectionTest\"}]}";
            mockMvc.perform(post("/fhir/Patient")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(patientJson));

            // Create an Observation
            String observationJson = "{\"resourceType\":\"Observation\",\"status\":\"final\",\"code\":{\"text\":\"Test\"}}";
            mockMvc.perform(post("/fhir/Observation")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(observationJson));

            // Verify collections exist
            assertTrue(mongoTemplate.collectionExists("patient"));
            assertTrue(mongoTemplate.collectionExists("observation"));
        }
    }
}
