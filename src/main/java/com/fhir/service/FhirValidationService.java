package com.fhir.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ValidationResult;
import com.fhir.exception.FhirValidationException;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FhirValidationService {

    private static final Logger logger = LoggerFactory.getLogger(FhirValidationService.class);

    @Autowired
    private FhirContext fhirContext;

    @Autowired
    private FhirValidator fhirValidator;

    public ValidationResult validate(IBaseResource resource) {
        return fhirValidator.validateWithResult(resource);
    }

    public ValidationResult validate(String resourceJson) {
        IBaseResource resource = fhirContext.newJsonParser().parseResource(resourceJson);
        return fhirValidator.validateWithResult(resource);
    }

    public void validateAndThrow(IBaseResource resource) {
        ValidationResult result = validate(resource);
        if (!result.isSuccessful()) {
            List<String> errors = extractErrors(result);
            logger.warn("Validation failed for resource: {}", errors);
            throw new FhirValidationException(errors);
        }
    }

    public void validateAndThrow(String resourceJson) {
        IBaseResource resource = fhirContext.newJsonParser().parseResource(resourceJson);
        validateAndThrow(resource);
    }

    public OperationOutcome toOperationOutcome(ValidationResult result) {
        OperationOutcome outcome = new OperationOutcome();

        result.getMessages().forEach(message -> {
            OperationOutcome.OperationOutcomeIssueComponent issue = outcome.addIssue();

            switch (message.getSeverity()) {
                case ERROR -> issue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
                case WARNING -> issue.setSeverity(OperationOutcome.IssueSeverity.WARNING);
                case INFORMATION -> issue.setSeverity(OperationOutcome.IssueSeverity.INFORMATION);
                default -> issue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
            }

            issue.setCode(OperationOutcome.IssueType.PROCESSING);
            issue.setDiagnostics(message.getMessage());
            issue.addLocation(message.getLocationString());
        });

        return outcome;
    }

    public boolean isValid(IBaseResource resource) {
        return validate(resource).isSuccessful();
    }

    public boolean isValid(String resourceJson) {
        try {
            return validate(resourceJson).isSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    private List<String> extractErrors(ValidationResult result) {
        return result.getMessages().stream()
                .filter(m -> m.getSeverity() == ca.uhn.fhir.validation.ResultSeverityEnum.ERROR ||
                        m.getSeverity() == ca.uhn.fhir.validation.ResultSeverityEnum.FATAL)
                .map(m -> m.getLocationString() + ": " + m.getMessage())
                .collect(Collectors.toList());
    }
}
