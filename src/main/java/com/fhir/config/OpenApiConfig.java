package com.fhir.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${fhir.server.base-url}")
    private String baseUrl;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FHIR R4 Server API")
                        .version("1.0.0")
                        .description("Spring Boot FHIR R4 Server with MongoDB - Supports all FHIR resource types and operations")
                        .contact(new Contact()
                                .name("FHIR Support")
                                .email("support@fhir.example.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Development Server"),
                        new Server().url(baseUrl).description("FHIR Server")
                ))
                .tags(List.of(
                        new Tag().name("FHIR System").description("System-level FHIR operations"),
                        new Tag().name("FHIR Resources").description("Generic FHIR resource operations"),
                        new Tag().name("Patient").description("Patient resource operations"),
                        new Tag().name("Practitioner").description("Practitioner resource operations"),
                        new Tag().name("Organization").description("Organization resource operations"),
                        new Tag().name("Observation").description("Observation resource operations"),
                        new Tag().name("Condition").description("Condition resource operations"),
                        new Tag().name("Encounter").description("Encounter resource operations"),
                        new Tag().name("MedicationRequest").description("MedicationRequest resource operations")
                ));
    }

    @Bean
    public GroupedOpenApi allApis() {
        return GroupedOpenApi.builder()
                .group("all")
                .displayName("All APIs")
                .pathsToMatch("/**")
                .build();
    }

    @Bean
    public GroupedOpenApi fhirApis() {
        return GroupedOpenApi.builder()
                .group("fhir")
                .displayName("FHIR APIs")
                .pathsToMatch("/fhir/**")
                .build();
    }

    @Bean
    public GroupedOpenApi resourceApis() {
        return GroupedOpenApi.builder()
                .group("resources")
                .displayName("Resource APIs")
                .pathsToMatch("/api/**")
                .build();
    }
}
