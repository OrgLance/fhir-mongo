package com.fhir.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.validation.FhirValidator;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FhirConfig Tests")
class FhirConfigTest {

    private FhirConfig config;

    @BeforeEach
    void setUp() {
        config = new FhirConfig();
    }

    @Test
    @DisplayName("Should create FhirContext bean for R4")
    void shouldCreateFhirContextBean() {
        FhirContext context = config.fhirContext();

        assertNotNull(context);
        assertEquals(FhirVersionEnum.R4, context.getVersion().getVersion());
    }

    @Test
    @DisplayName("Should create JSON parser bean")
    void shouldCreateJsonParserBean() {
        FhirContext context = config.fhirContext();
        IParser parser = config.fhirJsonParser(context);

        assertNotNull(parser);
    }

    @Test
    @DisplayName("Should create XML parser bean")
    void shouldCreateXmlParserBean() {
        FhirContext context = config.fhirContext();
        IParser parser = config.fhirXmlParser(context);

        assertNotNull(parser);
    }

    @Test
    @DisplayName("Should create FhirValidator bean")
    void shouldCreateFhirValidatorBean() {
        FhirContext context = config.fhirContext();
        FhirValidator validator = config.fhirValidator(context);

        assertNotNull(validator);
    }
}
