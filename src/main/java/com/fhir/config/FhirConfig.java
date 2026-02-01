package com.fhir.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.validation.FhirValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FhirConfig {

    @Bean
    public FhirContext fhirContext() {
        return FhirContext.forR4();
    }

    @Bean
    public IParser fhirJsonParser(FhirContext fhirContext) {
        return fhirContext.newJsonParser().setPrettyPrint(true);
    }

    @Bean
    public IParser fhirXmlParser(FhirContext fhirContext) {
        return fhirContext.newXmlParser().setPrettyPrint(true);
    }

    @Bean
    public FhirValidator fhirValidator(FhirContext fhirContext) {
        return fhirContext.newValidator();
    }
}
