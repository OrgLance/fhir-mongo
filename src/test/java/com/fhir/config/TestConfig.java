package com.fhir.config;

import ca.uhn.fhir.context.FhirContext;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test configuration to provide common beans for unit tests.
 */
@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    public FhirContext fhirContext() {
        return FhirContext.forR4();
    }
}
