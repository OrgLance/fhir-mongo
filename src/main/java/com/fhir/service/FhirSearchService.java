package com.fhir.service;

import com.fhir.model.CursorPage;
import com.fhir.model.FhirResourceDocument;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

/**
 * FHIR Search Service - supports search parameters for all resource types.
 * Uses dynamic collection names based on resource type.
 */
@Service
public class FhirSearchService {

    private static final Logger logger = LoggerFactory.getLogger(FhirSearchService.class);
    private static final Pattern DATE_PREFIX_PATTERN = Pattern.compile("^(eq|ne|lt|gt|le|ge|sa|eb|ap)?(.+)$");

    @Autowired
    private MongoTemplate mongoTemplate;

    /**
     * Get collection name for a resource type (lowercase)
     */
    private String getCollectionName(String resourceType) {
        return resourceType.toLowerCase();
    }

    /**
     * Search for resources with parameters
     */
    public Page<FhirResourceDocument> search(String resourceType, Map<String, String> params, Pageable pageable) {
        String collectionName = getCollectionName(resourceType);
        Query query = buildQuery(resourceType, params);
        query.with(pageable);

        logger.debug("Searching in collection '{}' with query: {}", collectionName, query);

        List<FhirResourceDocument> results = mongoTemplate.find(query, FhirResourceDocument.class, collectionName);

        // Get total count for pagination
        Query countQuery = Query.of(query).limit(-1).skip(-1);
        long total = mongoTemplate.count(countQuery, FhirResourceDocument.class, collectionName);

        logger.debug("Found {} results (total: {}) in collection '{}'", results.size(), total, collectionName);

        return new PageImpl<>(results, pageable, total);
    }

    private Query buildQuery(String resourceType, Map<String, String> params) {
        Query query = new Query();

        List<Criteria> criteriaList = new ArrayList<>();
        // Always filter out deleted resources
        criteriaList.add(Criteria.where("deleted").is(false));

        for (Map.Entry<String, String> entry : params.entrySet()) {
            String paramName = entry.getKey();
            String paramValue = entry.getValue();

            // Skip pagination parameters
            if (paramName.equals("_page") || paramName.equals("_count") || paramName.equals("_sort")) {
                continue;
            }

            if (paramName.startsWith("_")) {
                handleSystemParameter(criteriaList, paramName, paramValue);
            } else {
                handleSearchParameter(criteriaList, resourceType, paramName, paramValue);
            }
        }

        if (!criteriaList.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        }

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

        // Default: case-insensitive prefix match
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

        // Default: use parameter name as field path
        return paramName;
    }

    private Map<String, Map<String, String>> getSearchParamMappings() {
        Map<String, Map<String, String>> mappings = new HashMap<>();

        // Common parameters for all resources
        Map<String, String> common = new HashMap<>();
        common.put("_id", "id");
        common.put("_lastUpdated", "meta.lastUpdated");
        common.put("identifier", "identifier.value");
        common.put("status", "status");
        mappings.put("*", common);

        // Patient search parameters
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

        // Practitioner search parameters
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

        // Organization search parameters
        Map<String, String> organization = new HashMap<>();
        organization.put("name", "name");
        organization.put("type", "type.coding.code");
        organization.put("address", "address.line");
        organization.put("address-city", "address.city");
        organization.put("address-state", "address.state");
        organization.put("partof", "partOf.reference");
        organization.put("active", "active");
        mappings.put("Organization", organization);

        // Encounter search parameters
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

        // Observation search parameters
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

        // Condition search parameters
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

        // Medication search parameters
        Map<String, String> medication = new HashMap<>();
        medication.put("code", "code.coding.code");
        medication.put("form", "form.coding.code");
        medication.put("manufacturer", "manufacturer.reference");
        mappings.put("Medication", medication);

        // MedicationRequest search parameters
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

        // Procedure search parameters
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

        // DiagnosticReport search parameters
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

        // Immunization search parameters
        Map<String, String> immunization = new HashMap<>();
        immunization.put("patient", "patient.reference");
        immunization.put("vaccine-code", "vaccineCode.coding.code");
        immunization.put("date", "occurrenceDateTime");
        immunization.put("location", "location.reference");
        immunization.put("performer", "performer.actor.reference");
        immunization.put("reaction", "reaction.detail.reference");
        immunization.put("reason-code", "reasonCode.coding.code");
        mappings.put("Immunization", immunization);

        // AllergyIntolerance search parameters
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

        // Appointment search parameters
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

        // CarePlan search parameters
        Map<String, String> carePlan = new HashMap<>();
        carePlan.put("patient", "subject.reference");
        carePlan.put("subject", "subject.reference");
        carePlan.put("category", "category.coding.code");
        carePlan.put("date", "period.start");
        carePlan.put("encounter", "encounter.reference");
        carePlan.put("intent", "intent");
        carePlan.put("activity-code", "activity.detail.code.coding.code");
        mappings.put("CarePlan", carePlan);

        // CareTeam search parameters
        Map<String, String> careTeam = new HashMap<>();
        careTeam.put("patient", "subject.reference");
        careTeam.put("subject", "subject.reference");
        careTeam.put("category", "category.coding.code");
        careTeam.put("participant", "participant.member.reference");
        careTeam.put("encounter", "encounter.reference");
        mappings.put("CareTeam", careTeam);

        // Claim search parameters
        Map<String, String> claim = new HashMap<>();
        claim.put("patient", "patient.reference");
        claim.put("provider", "provider.reference");
        claim.put("created", "created");
        claim.put("priority", "priority.coding.code");
        claim.put("use", "use");
        claim.put("insurer", "insurer.reference");
        mappings.put("Claim", claim);

        // Coverage search parameters
        Map<String, String> coverage = new HashMap<>();
        coverage.put("patient", "beneficiary.reference");
        coverage.put("beneficiary", "beneficiary.reference");
        coverage.put("payor", "payor.reference");
        coverage.put("subscriber", "subscriber.reference");
        coverage.put("type", "type.coding.code");
        mappings.put("Coverage", coverage);

        // DocumentReference search parameters
        Map<String, String> documentReference = new HashMap<>();
        documentReference.put("patient", "subject.reference");
        documentReference.put("subject", "subject.reference");
        documentReference.put("type", "type.coding.code");
        documentReference.put("category", "category.coding.code");
        documentReference.put("date", "date");
        documentReference.put("author", "author.reference");
        documentReference.put("custodian", "custodian.reference");
        documentReference.put("encounter", "context.encounter.reference");
        mappings.put("DocumentReference", documentReference);

        // Goal search parameters
        Map<String, String> goal = new HashMap<>();
        goal.put("patient", "subject.reference");
        goal.put("subject", "subject.reference");
        goal.put("category", "category.coding.code");
        goal.put("lifecycle-status", "lifecycleStatus");
        goal.put("target-date", "target.dueDate");
        goal.put("achievement-status", "achievementStatus.coding.code");
        mappings.put("Goal", goal);

        // ServiceRequest search parameters
        Map<String, String> serviceRequest = new HashMap<>();
        serviceRequest.put("patient", "subject.reference");
        serviceRequest.put("subject", "subject.reference");
        serviceRequest.put("code", "code.coding.code");
        serviceRequest.put("category", "category.coding.code");
        serviceRequest.put("encounter", "encounter.reference");
        serviceRequest.put("requester", "requester.reference");
        serviceRequest.put("performer", "performer.reference");
        serviceRequest.put("authored", "authoredOn");
        serviceRequest.put("intent", "intent");
        serviceRequest.put("priority", "priority");
        mappings.put("ServiceRequest", serviceRequest);

        // Location search parameters
        Map<String, String> location = new HashMap<>();
        location.put("name", "name");
        location.put("address", "address.line");
        location.put("address-city", "address.city");
        location.put("address-state", "address.state");
        location.put("type", "type.coding.code");
        location.put("organization", "managingOrganization.reference");
        location.put("partof", "partOf.reference");
        mappings.put("Location", location);

        // Device search parameters
        Map<String, String> device = new HashMap<>();
        device.put("patient", "patient.reference");
        device.put("type", "type.coding.code");
        device.put("manufacturer", "manufacturer");
        device.put("model", "modelNumber");
        device.put("udi-carrier", "udiCarrier.carrierHRF");
        device.put("location", "location.reference");
        device.put("organization", "owner.reference");
        mappings.put("Device", device);

        // Specimen search parameters
        Map<String, String> specimen = new HashMap<>();
        specimen.put("patient", "subject.reference");
        specimen.put("subject", "subject.reference");
        specimen.put("type", "type.coding.code");
        specimen.put("collected", "collection.collectedDateTime");
        specimen.put("collector", "collection.collector.reference");
        specimen.put("bodysite", "collection.bodySite.coding.code");
        mappings.put("Specimen", specimen);

        // Media search parameters
        Map<String, String> media = new HashMap<>();
        media.put("patient", "subject.reference");
        media.put("subject", "subject.reference");
        media.put("type", "type.coding.code");
        media.put("created", "createdDateTime");
        media.put("encounter", "encounter.reference");
        media.put("operator", "operator.reference");
        mappings.put("Media", media);

        return mappings;
    }

    /**
     * Search with cursor-based pagination for O(1) performance.
     * Uses MongoDB _id for efficient cursor navigation.
     *
     * @param resourceType The FHIR resource type
     * @param params       Search parameters
     * @param cursor       The cursor (last _id from previous page), null for first page
     * @param count        Number of results per page
     * @return CursorPage with results and navigation cursors
     */
    public CursorPage<FhirResourceDocument> searchWithCursor(String resourceType, Map<String, String> params,
                                                              String cursor, int count) {
        String collectionName = getCollectionName(resourceType);
        Query query = buildQuery(resourceType, params);

        // Add cursor condition for pagination
        if (cursor != null && !cursor.isEmpty()) {
            try {
                ObjectId cursorId = new ObjectId(cursor);
                query.addCriteria(Criteria.where("_id").gt(cursorId));
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid cursor format: {}", cursor);
            }
        }

        // Sort by _id for consistent cursor pagination
        query.with(Sort.by(Sort.Direction.ASC, "_id"));

        // Fetch one extra to check if there's a next page
        query.limit(count + 1);

        logger.debug("Cursor search in collection '{}' with query: {}", collectionName, query);

        List<FhirResourceDocument> results = mongoTemplate.find(query, FhirResourceDocument.class, collectionName);

        // Determine if there's a next page
        boolean hasNext = results.size() > count;
        if (hasNext) {
            results = results.subList(0, count);
        }

        // Get next cursor from last result
        String nextCursor = null;
        if (hasNext && !results.isEmpty()) {
            FhirResourceDocument lastDoc = results.get(results.size() - 1);
            nextCursor = lastDoc.getId();
        }

        // Get previous cursor from first result (for bidirectional navigation)
        String previousCursor = null;
        if (cursor != null && !results.isEmpty()) {
            previousCursor = cursor;
        }

        logger.debug("Cursor search found {} results, hasNext: {}", results.size(), hasNext);

        return CursorPage.<FhirResourceDocument>builder()
                .content(results)
                .hasNext(hasNext)
                .hasPrevious(cursor != null && !cursor.isEmpty())
                .nextCursor(nextCursor)
                .previousCursor(previousCursor)
                .size(results.size())
                .build();
    }
}
