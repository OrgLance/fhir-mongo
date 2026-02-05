package com.fhir.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ValidationResult;
import com.fhir.exception.FhirValidationException;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FhirValidationService Tests")
class FhirValidationServiceTest {

    @Spy
    private FhirContext fhirContext = FhirContext.forR4();

    @Spy
    private FhirValidator fhirValidator = fhirContext.newValidator();

    @InjectMocks
    private FhirValidationService validationService;

    private Patient validPatient;
    private String validPatientJson;

    @BeforeEach
    void setUp() {
        validPatient = new Patient();
        validPatient.setId("p-123");
        validPatient.addName().setFamily("Smith").addGiven("John");

        validPatientJson = fhirContext.newJsonParser().encodeResourceToString(validPatient);
    }

    @Nested
    @DisplayName("Validate Resource Tests")
    class ValidateResourceTests {

        @Test
        @DisplayName("Should validate valid patient resource")
        void shouldValidateValidPatientResource() {
            ValidationResult result = validationService.validate(validPatient);

            assertNotNull(result);
            assertTrue(result.isSuccessful());
        }

        @Test
        @DisplayName("Should validate patient resource from JSON string")
        void shouldValidatePatientResourceFromJsonString() {
            ValidationResult result = validationService.validate(validPatientJson);

            assertNotNull(result);
            assertTrue(result.isSuccessful());
        }
    }

    @Nested
    @DisplayName("Validate and Throw Tests")
    class ValidateAndThrowTests {

        @Test
        @DisplayName("Should not throw for valid resource")
        void shouldNotThrowForValidResource() {
            assertDoesNotThrow(() -> validationService.validateAndThrow(validPatient));
        }

        @Test
        @DisplayName("Should not throw for valid JSON")
        void shouldNotThrowForValidJson() {
            assertDoesNotThrow(() -> validationService.validateAndThrow(validPatientJson));
        }
    }

    @Nested
    @DisplayName("IsValid Tests")
    class IsValidTests {

        @Test
        @DisplayName("Should return true for valid resource")
        void shouldReturnTrueForValidResource() {
            assertTrue(validationService.isValid(validPatient));
        }

        @Test
        @DisplayName("Should return true for valid JSON")
        void shouldReturnTrueForValidJson() {
            assertTrue(validationService.isValid(validPatientJson));
        }

        @Test
        @DisplayName("Should return false for invalid JSON")
        void shouldReturnFalseForInvalidJson() {
            assertFalse(validationService.isValid("invalid json"));
        }
    }

    @Nested
    @DisplayName("To Operation Outcome Tests")
    class ToOperationOutcomeTests {

        @Test
        @DisplayName("Should convert validation result to operation outcome")
        void shouldConvertValidationResultToOperationOutcome() {
            ValidationResult result = validationService.validate(validPatient);
            OperationOutcome outcome = validationService.toOperationOutcome(result);

            assertNotNull(outcome);
        }
    }
}
