package com.fhir.service;

import com.fhir.model.FhirResourceDocument;
import com.fhir.repository.FhirResourceRepository;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FhirSearchService {

    private static final Logger logger = LoggerFactory.getLogger(FhirSearchService.class);
    private static final Pattern DATE_PREFIX_PATTERN = Pattern.compile("^(eq|ne|lt|gt|le|ge|sa|eb|ap)?(.+)$");

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private FhirResourceRepository resourceRepository;

    public Page<FhirResourceDocument> search(String resourceType, Map<String, String> params, Pageable pageable) {
        Query query = buildQuery(resourceType, params);
        query.with(pageable);

        List<FhirResourceDocument> results = mongoTemplate.find(query, FhirResourceDocument.class);
        long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), FhirResourceDocument.class);

        return new PageImpl<>(results, pageable, total);
    }

    private Query buildQuery(String resourceType, Map<String, String> params) {
        Query query = new Query();

        List<Criteria> criteriaList = new ArrayList<>();
        criteriaList.add(Criteria.where("resourceType").is(resourceType));
        criteriaList.add(Criteria.where("deleted").is(false));

        for (Map.Entry<String, String> entry : params.entrySet()) {
            String paramName = entry.getKey();
            String paramValue = entry.getValue();

            if (paramName.startsWith("_")) {
                handleSystemParameter(criteriaList, paramName, paramValue);
            } else {
                handleSearchParameter(criteriaList, resourceType, paramName, paramValue);
            }
        }

        query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        return query;
    }

    private void handleSystemParameter(List<Criteria> criteriaList, String paramName, String paramValue) {
        switch (paramName) {
            case "_id" -> criteriaList.add(Criteria.where("resourceId").is(paramValue));
            case "_lastUpdated" -> handleDateParameter(criteriaList, "lastUpdated", paramValue);
            case "_tag" -> criteriaList.add(Criteria.where("resourceData.meta.tag.code").is(paramValue));
            case "_profile" -> criteriaList.add(Criteria.where("resourceData.meta.profile").is(paramValue));
            case "_security" -> criteriaList.add(Criteria.where("resourceData.meta.security.code").is(paramValue));
            case "_text", "_content" -> criteriaList.add(Criteria.where("resourceJson").regex(paramValue, "i"));
            default -> logger.debug("Ignoring unsupported system parameter: {}", paramName);
        }
    }

    private void handleSearchParameter(List<Criteria> criteriaList, String resourceType, String paramName, String paramValue) {
        String modifier = null;
        String actualParamName = paramName;

        if (paramName.contains(":")) {
            String[] parts = paramName.split(":", 2);
            actualParamName = parts[0];
            modifier = parts[1];
        }

        String fieldPath = mapSearchParamToField(resourceType, actualParamName);
        if (fieldPath == null) {
            logger.debug("Unknown search parameter: {} for resource type: {}", actualParamName, resourceType);
            return;
        }

        Criteria criteria = buildCriteria(fieldPath, paramValue, modifier);
        if (criteria != null) {
            criteriaList.add(criteria);
        }
    }

    private Criteria buildCriteria(String fieldPath, String value, String modifier) {
        String fullPath = "resourceData." + fieldPath;

        if (modifier != null) {
            return switch (modifier) {
                case "exact" -> Criteria.where(fullPath).is(value);
                case "contains" -> Criteria.where(fullPath).regex(value, "i");
                case "missing" -> Boolean.parseBoolean(value) ?
                        Criteria.where(fullPath).exists(false) :
                        Criteria.where(fullPath).exists(true);
                case "not" -> Criteria.where(fullPath).ne(value);
                case "above", "below" -> handleHierarchicalModifier(fullPath, value, modifier);
                default -> Criteria.where(fullPath).regex("^" + Pattern.quote(value), "i");
            };
        }

        if (value.contains(",")) {
            String[] values = value.split(",");
            return Criteria.where(fullPath).in(Arrays.asList(values));
        }

        return Criteria.where(fullPath).regex("^" + Pattern.quote(value), "i");
    }

    private Criteria handleHierarchicalModifier(String fieldPath, String value, String modifier) {
        return Criteria.where(fieldPath).is(value);
    }

    private void handleDateParameter(List<Criteria> criteriaList, String fieldName, String value) {
        Matcher matcher = DATE_PREFIX_PATTERN.matcher(value);
        if (!matcher.matches()) {
            return;
        }

        String prefix = matcher.group(1);
        String dateValue = matcher.group(2);

        if (prefix == null) {
            prefix = "eq";
        }

        try {
            Instant instant = parseDate(dateValue);

            Criteria criteria = switch (prefix) {
                case "eq" -> Criteria.where(fieldName).is(instant);
                case "ne" -> Criteria.where(fieldName).ne(instant);
                case "lt" -> Criteria.where(fieldName).lt(instant);
                case "gt" -> Criteria.where(fieldName).gt(instant);
                case "le" -> Criteria.where(fieldName).lte(instant);
                case "ge" -> Criteria.where(fieldName).gte(instant);
                case "sa" -> Criteria.where(fieldName).gt(instant);
                case "eb" -> Criteria.where(fieldName).lt(instant);
                case "ap" -> {
                    Instant start = instant.minusSeconds(86400);
                    Instant end = instant.plusSeconds(86400);
                    yield Criteria.where(fieldName).gte(start).lte(end);
                }
                default -> null;
            };

            if (criteria != null) {
                criteriaList.add(criteria);
            }
        } catch (Exception e) {
            logger.warn("Failed to parse date value: {}", dateValue);
        }
    }

    private Instant parseDate(String dateValue) {
        if (dateValue.length() == 4) {
            return LocalDate.of(Integer.parseInt(dateValue), 1, 1)
                    .atStartOfDay().toInstant(ZoneOffset.UTC);
        } else if (dateValue.length() == 7) {
            String[] parts = dateValue.split("-");
            return LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), 1)
                    .atStartOfDay().toInstant(ZoneOffset.UTC);
        } else if (dateValue.length() == 10) {
            return LocalDate.parse(dateValue, DateTimeFormatter.ISO_DATE)
                    .atStartOfDay().toInstant(ZoneOffset.UTC);
        } else {
            return Instant.parse(dateValue);
        }
    }

    private String mapSearchParamToField(String resourceType, String paramName) {
        Map<String, Map<String, String>> searchParamMappings = getSearchParamMappings();

        Map<String, String> resourceMappings = searchParamMappings.get(resourceType);
        if (resourceMappings != null && resourceMappings.containsKey(paramName)) {
            return resourceMappings.get(paramName);
        }

        Map<String, String> commonMappings = searchParamMappings.get("*");
        if (commonMappings != null && commonMappings.containsKey(paramName)) {
            return commonMappings.get(paramName);
        }

        return paramName;
    }

    private Map<String, Map<String, String>> getSearchParamMappings() {
        Map<String, Map<String, String>> mappings = new HashMap<>();

        Map<String, String> common = new HashMap<>();
        common.put("_id", "id");
        common.put("_lastUpdated", "meta.lastUpdated");
        common.put("identifier", "identifier.value");
        common.put("status", "status");
        mappings.put("*", common);

        Map<String, String> patient = new HashMap<>();
        patient.put("name", "name.family");
        patient.put("family", "name.family");
        patient.put("given", "name.given");
        patient.put("birthdate", "birthDate");
        patient.put("gender", "gender");
        patient.put("phone", "telecom.value");
        patient.put("email", "telecom.value");
        patient.put("address", "address.line");
        patient.put("address-city", "address.city");
        patient.put("address-state", "address.state");
        patient.put("address-postalcode", "address.postalCode");
        patient.put("address-country", "address.country");
        patient.put("organization", "managingOrganization.reference");
        patient.put("general-practitioner", "generalPractitioner.reference");
        patient.put("active", "active");
        patient.put("deceased", "deceasedBoolean");
        mappings.put("Patient", patient);

        Map<String, String> practitioner = new HashMap<>();
        practitioner.put("name", "name.family");
        practitioner.put("family", "name.family");
        practitioner.put("given", "name.given");
        practitioner.put("phone", "telecom.value");
        practitioner.put("email", "telecom.value");
        practitioner.put("address", "address.line");
        practitioner.put("active", "active");
        practitioner.put("communication", "communication.coding.code");
        mappings.put("Practitioner", practitioner);

        Map<String, String> organization = new HashMap<>();
        organization.put("name", "name");
        organization.put("type", "type.coding.code");
        organization.put("address", "address.line");
        organization.put("address-city", "address.city");
        organization.put("address-state", "address.state");
        organization.put("partof", "partOf.reference");
        organization.put("active", "active");
        mappings.put("Organization", organization);

        Map<String, String> encounter = new HashMap<>();
        encounter.put("patient", "subject.reference");
        encounter.put("subject", "subject.reference");
        encounter.put("date", "period.start");
        encounter.put("class", "class.code");
        encounter.put("type", "type.coding.code");
        encounter.put("participant", "participant.individual.reference");
        encounter.put("location", "location.location.reference");
        encounter.put("service-provider", "serviceProvider.reference");
        encounter.put("reason-code", "reasonCode.coding.code");
        mappings.put("Encounter", encounter);

        Map<String, String> observation = new HashMap<>();
        observation.put("patient", "subject.reference");
        observation.put("subject", "subject.reference");
        observation.put("code", "code.coding.code");
        observation.put("category", "category.coding.code");
        observation.put("date", "effectiveDateTime");
        observation.put("value-quantity", "valueQuantity.value");
        observation.put("value-concept", "valueCodeableConcept.coding.code");
        observation.put("performer", "performer.reference");
        observation.put("encounter", "encounter.reference");
        mappings.put("Observation", observation);

        Map<String, String> condition = new HashMap<>();
        condition.put("patient", "subject.reference");
        condition.put("subject", "subject.reference");
        condition.put("code", "code.coding.code");
        condition.put("clinical-status", "clinicalStatus.coding.code");
        condition.put("verification-status", "verificationStatus.coding.code");
        condition.put("category", "category.coding.code");
        condition.put("severity", "severity.coding.code");
        condition.put("onset-date", "onsetDateTime");
        condition.put("recorded-date", "recordedDate");
        condition.put("encounter", "encounter.reference");
        condition.put("asserter", "asserter.reference");
        mappings.put("Condition", condition);

        Map<String, String> medication = new HashMap<>();
        medication.put("code", "code.coding.code");
        medication.put("form", "form.coding.code");
        medication.put("manufacturer", "manufacturer.reference");
        mappings.put("Medication", medication);

        Map<String, String> medicationRequest = new HashMap<>();
        medicationRequest.put("patient", "subject.reference");
        medicationRequest.put("subject", "subject.reference");
        medicationRequest.put("medication", "medicationReference.reference");
        medicationRequest.put("code", "medicationCodeableConcept.coding.code");
        medicationRequest.put("requester", "requester.reference");
        medicationRequest.put("encounter", "encounter.reference");
        medicationRequest.put("authoredon", "authoredOn");
        medicationRequest.put("intent", "intent");
        medicationRequest.put("priority", "priority");
        mappings.put("MedicationRequest", medicationRequest);

        Map<String, String> procedure = new HashMap<>();
        procedure.put("patient", "subject.reference");
        procedure.put("subject", "subject.reference");
        procedure.put("code", "code.coding.code");
        procedure.put("date", "performedDateTime");
        procedure.put("encounter", "encounter.reference");
        procedure.put("performer", "performer.actor.reference");
        procedure.put("location", "location.reference");
        procedure.put("category", "category.coding.code");
        mappings.put("Procedure", procedure);

        Map<String, String> diagnosticReport = new HashMap<>();
        diagnosticReport.put("patient", "subject.reference");
        diagnosticReport.put("subject", "subject.reference");
        diagnosticReport.put("code", "code.coding.code");
        diagnosticReport.put("date", "effectiveDateTime");
        diagnosticReport.put("category", "category.coding.code");
        diagnosticReport.put("performer", "performer.reference");
        diagnosticReport.put("encounter", "encounter.reference");
        diagnosticReport.put("result", "result.reference");
        diagnosticReport.put("conclusion", "conclusion");
        mappings.put("DiagnosticReport", diagnosticReport);

        Map<String, String> immunization = new HashMap<>();
        immunization.put("patient", "patient.reference");
        immunization.put("vaccine-code", "vaccineCode.coding.code");
        immunization.put("date", "occurrenceDateTime");
        immunization.put("location", "location.reference");
        immunization.put("performer", "performer.actor.reference");
        immunization.put("reaction", "reaction.detail.reference");
        immunization.put("reason-code", "reasonCode.coding.code");
        mappings.put("Immunization", immunization);

        Map<String, String> allergyIntolerance = new HashMap<>();
        allergyIntolerance.put("patient", "patient.reference");
        allergyIntolerance.put("code", "code.coding.code");
        allergyIntolerance.put("clinical-status", "clinicalStatus.coding.code");
        allergyIntolerance.put("verification-status", "verificationStatus.coding.code");
        allergyIntolerance.put("type", "type");
        allergyIntolerance.put("category", "category");
        allergyIntolerance.put("criticality", "criticality");
        allergyIntolerance.put("recorder", "recorder.reference");
        allergyIntolerance.put("asserter", "asserter.reference");
        allergyIntolerance.put("onset", "onsetDateTime");
        mappings.put("AllergyIntolerance", allergyIntolerance);

        Map<String, String> appointment = new HashMap<>();
        appointment.put("patient", "participant.actor.reference");
        appointment.put("actor", "participant.actor.reference");
        appointment.put("date", "start");
        appointment.put("service-type", "serviceType.coding.code");
        appointment.put("specialty", "specialty.coding.code");
        appointment.put("appointment-type", "appointmentType.coding.code");
        appointment.put("location", "participant.actor.reference");
        appointment.put("practitioner", "participant.actor.reference");
        appointment.put("reason-code", "reasonCode.coding.code");
        mappings.put("Appointment", appointment);

        return mappings;
    }
}
