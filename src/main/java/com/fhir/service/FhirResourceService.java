package com.fhir.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fhir.exception.FhirResourceNotFoundException;
import com.fhir.exception.FhirValidationException;
import com.fhir.model.FhirResourceDocument;
import com.fhir.model.FhirResourceHistory;
import com.fhir.repository.FhirResourceHistoryRepository;
import com.fhir.repository.FhirResourceRepository;
import org.bson.Document;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class FhirResourceService {

    private static final Logger logger = LoggerFactory.getLogger(FhirResourceService.class);

    @Autowired
    private FhirResourceRepository resourceRepository;

    @Autowired
    private FhirResourceHistoryRepository historyRepository;

    @Autowired
    private FhirContext fhirContext;

    @Value("${fhir.server.base-url}")
    private String baseUrl;

    @Value("${fhir.server.default-page-size:20}")
    private int defaultPageSize;

    @Value("${fhir.server.max-page-size:100}")
    private int maxPageSize;

    public <T extends IBaseResource> T create(T resource) {
        String resourceType = fhirContext.getResourceType(resource);
        String resourceId = generateResourceId();

        if (resource instanceof Resource) {
            ((Resource) resource).setId(resourceId);
            Meta meta = ((Resource) resource).getMeta();
            if (meta == null) {
                meta = new Meta();
                ((Resource) resource).setMeta(meta);
            }
            meta.setVersionId("1");
            meta.setLastUpdated(Date.from(Instant.now()));
        }

        IParser jsonParser = fhirContext.newJsonParser();
        String resourceJson = jsonParser.encodeResourceToString(resource);

        FhirResourceDocument doc = FhirResourceDocument.builder()
                .resourceType(resourceType)
                .resourceId(resourceId)
                .resourceJson(resourceJson)
                .resourceData(Document.parse(resourceJson))
                .versionId(1L)
                .lastUpdated(Instant.now())
                .createdAt(Instant.now())
                .active(true)
                .deleted(false)
                .build();

        resourceRepository.save(doc);

        saveHistory(resourceType, resourceId, 1L, resourceJson, "CREATE");

        logger.info("Created {} with id: {}", resourceType, resourceId);
        return resource;
    }

    public <T extends IBaseResource> T read(String resourceType, String resourceId) {
        FhirResourceDocument doc = resourceRepository
                .findByResourceTypeAndResourceIdAndDeletedFalse(resourceType, resourceId)
                .orElseThrow(() -> new FhirResourceNotFoundException(resourceType, resourceId));

        return parseResource(doc.getResourceJson());
    }

    public <T extends IBaseResource> T vread(String resourceType, String resourceId, String versionId) {
        Long version = Long.parseLong(versionId);
        FhirResourceHistory history = historyRepository
                .findByResourceTypeAndResourceIdAndVersionId(resourceType, resourceId, version)
                .orElseThrow(() -> new FhirResourceNotFoundException(resourceType, resourceId + "/_history/" + versionId));

        return parseResource(history.getResourceJson());
    }

    @Transactional
    public <T extends IBaseResource> T update(String resourceType, String resourceId, T resource) {
        FhirResourceDocument existingDoc = resourceRepository
                .findByResourceTypeAndResourceId(resourceType, resourceId)
                .orElse(null);

        Long newVersion;
        Instant now = Instant.now();

        if (existingDoc == null) {
            newVersion = 1L;
            if (resource instanceof Resource) {
                ((Resource) resource).setId(resourceId);
            }
        } else {
            if (existingDoc.getDeleted() != null && existingDoc.getDeleted()) {
                throw new FhirValidationException("Cannot update a deleted resource");
            }
            newVersion = existingDoc.getVersionId() + 1;
        }

        if (resource instanceof Resource) {
            Meta meta = ((Resource) resource).getMeta();
            if (meta == null) {
                meta = new Meta();
                ((Resource) resource).setMeta(meta);
            }
            meta.setVersionId(String.valueOf(newVersion));
            meta.setLastUpdated(Date.from(now));
        }

        IParser jsonParser = fhirContext.newJsonParser();
        String resourceJson = jsonParser.encodeResourceToString(resource);

        FhirResourceDocument doc = FhirResourceDocument.builder()
                .id(existingDoc != null ? existingDoc.getId() : null)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .resourceJson(resourceJson)
                .resourceData(Document.parse(resourceJson))
                .versionId(newVersion)
                .lastUpdated(now)
                .createdAt(existingDoc != null ? existingDoc.getCreatedAt() : now)
                .active(true)
                .deleted(false)
                .build();

        resourceRepository.save(doc);

        saveHistory(resourceType, resourceId, newVersion, resourceJson, "UPDATE");

        logger.info("Updated {} with id: {} to version: {}", resourceType, resourceId, newVersion);
        return resource;
    }

    @Transactional
    public void delete(String resourceType, String resourceId) {
        FhirResourceDocument doc = resourceRepository
                .findByResourceTypeAndResourceIdAndDeletedFalse(resourceType, resourceId)
                .orElseThrow(() -> new FhirResourceNotFoundException(resourceType, resourceId));

        doc.setDeleted(true);
        doc.setActive(false);
        doc.setLastUpdated(Instant.now());
        doc.setVersionId(doc.getVersionId() + 1);

        resourceRepository.save(doc);

        saveHistory(resourceType, resourceId, doc.getVersionId(), doc.getResourceJson(), "DELETE");

        logger.info("Deleted {} with id: {}", resourceType, resourceId);
    }

    public Bundle search(String resourceType, Map<String, String> searchParams, int page, int count) {
        int pageSize = Math.min(count > 0 ? count : defaultPageSize, maxPageSize);
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "lastUpdated"));

        Page<FhirResourceDocument> results = resourceRepository
                .findByResourceTypeAndDeletedFalse(resourceType, pageable);

        return createSearchBundle(results, resourceType, searchParams, page, pageSize);
    }

    public Bundle searchAll(Map<String, String> searchParams, int page, int count) {
        int pageSize = Math.min(count > 0 ? count : defaultPageSize, maxPageSize);
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "lastUpdated"));

        Page<FhirResourceDocument> results = resourceRepository.findByDeletedFalse(pageable);

        return createSystemSearchBundle(results, searchParams, page, pageSize);
    }

    public Bundle history(String resourceType, String resourceId, int page, int count) {
        int pageSize = Math.min(count > 0 ? count : defaultPageSize, maxPageSize);
        Pageable pageable = PageRequest.of(page, pageSize);

        Page<FhirResourceHistory> historyPage = historyRepository
                .findByResourceTypeAndResourceIdOrderByVersionIdDesc(resourceType, resourceId, pageable);

        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.HISTORY);
        bundle.setTotal((int) historyPage.getTotalElements());
        bundle.setTimestamp(Date.from(Instant.now()));

        for (FhirResourceHistory history : historyPage.getContent()) {
            Bundle.BundleEntryComponent entry = bundle.addEntry();
            entry.setFullUrl(baseUrl + "/" + resourceType + "/" + resourceId + "/_history/" + history.getVersionId());

            IBaseResource resource = parseResource(history.getResourceJson());
            entry.setResource((Resource) resource);

            Bundle.BundleEntryRequestComponent request = new Bundle.BundleEntryRequestComponent();
            request.setMethod(getHttpMethod(history.getAction()));
            request.setUrl(resourceType + "/" + resourceId);
            entry.setRequest(request);

            Bundle.BundleEntryResponseComponent response = new Bundle.BundleEntryResponseComponent();
            response.setStatus("200");
            response.setLastModified(Date.from(history.getTimestamp()));
            response.setEtag("W/\"" + history.getVersionId() + "\"");
            entry.setResponse(response);
        }

        addPaginationLinks(bundle, resourceType + "/" + resourceId + "/_history", page, pageSize, historyPage.getTotalPages());

        return bundle;
    }

    public Bundle typeHistory(String resourceType, int page, int count) {
        int pageSize = Math.min(count > 0 ? count : defaultPageSize, maxPageSize);
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "lastUpdated"));

        Page<FhirResourceDocument> resources = resourceRepository
                .findByResourceTypeAndDeletedFalse(resourceType, pageable);

        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.HISTORY);
        bundle.setTotal((int) resources.getTotalElements());
        bundle.setTimestamp(Date.from(Instant.now()));

        for (FhirResourceDocument doc : resources.getContent()) {
            Bundle.BundleEntryComponent entry = bundle.addEntry();
            entry.setFullUrl(baseUrl + "/" + doc.getResourceType() + "/" + doc.getResourceId());

            IBaseResource resource = parseResource(doc.getResourceJson());
            entry.setResource((Resource) resource);
        }

        addPaginationLinks(bundle, resourceType + "/_history", page, pageSize, resources.getTotalPages());

        return bundle;
    }

    public Bundle transaction(Bundle transactionBundle) {
        if (transactionBundle.getType() != Bundle.BundleType.TRANSACTION &&
                transactionBundle.getType() != Bundle.BundleType.BATCH) {
            throw new FhirValidationException("Bundle type must be 'transaction' or 'batch'");
        }

        Bundle responseBundle = new Bundle();
        responseBundle.setType(transactionBundle.getType() == Bundle.BundleType.TRANSACTION ?
                Bundle.BundleType.TRANSACTIONRESPONSE : Bundle.BundleType.BATCHRESPONSE);
        responseBundle.setTimestamp(Date.from(Instant.now()));

        Map<String, String> idMappings = new HashMap<>();

        for (Bundle.BundleEntryComponent entry : transactionBundle.getEntry()) {
            Bundle.BundleEntryComponent responseEntry = processTransactionEntry(entry, idMappings);
            responseBundle.addEntry(responseEntry);
        }

        return responseBundle;
    }

    public CapabilityStatement getCapabilityStatement() {
        CapabilityStatement cs = new CapabilityStatement();
        cs.setId("fhir-server-capability-statement");
        cs.setStatus(Enumerations.PublicationStatus.ACTIVE);
        cs.setDate(Date.from(Instant.now()));
        cs.setKind(CapabilityStatement.CapabilityStatementKind.INSTANCE);
        cs.setFhirVersion(Enumerations.FHIRVersion._4_0_1);
        cs.addFormat("json");
        cs.addFormat("xml");

        cs.setSoftware(new CapabilityStatement.CapabilityStatementSoftwareComponent()
                .setName("FHIR Spring Boot Server")
                .setVersion("1.0.0"));

        cs.setImplementation(new CapabilityStatement.CapabilityStatementImplementationComponent()
                .setDescription("FHIR R4 Server with MongoDB backend")
                .setUrl(baseUrl));

        CapabilityStatement.CapabilityStatementRestComponent rest = cs.addRest();
        rest.setMode(CapabilityStatement.RestfulCapabilityMode.SERVER);

        String[] resourceTypes = {
                "Patient", "Practitioner", "PractitionerRole", "Organization", "Location",
                "Encounter", "Condition", "Observation", "DiagnosticReport", "Procedure",
                "MedicationRequest", "Medication", "MedicationAdministration", "MedicationStatement",
                "AllergyIntolerance", "Immunization", "CarePlan", "CareTeam", "Goal",
                "ServiceRequest", "Appointment", "Schedule", "Slot", "Coverage", "Claim",
                "ClaimResponse", "ExplanationOfBenefit", "DocumentReference", "Binary",
                "Composition", "Bundle", "OperationOutcome", "ValueSet", "CodeSystem",
                "ConceptMap", "StructureDefinition", "SearchParameter", "CapabilityStatement",
                "Task", "Communication", "CommunicationRequest", "Device", "DeviceRequest",
                "Specimen", "ImagingStudy", "Media", "QuestionnaireResponse", "Questionnaire",
                "Consent", "Contract", "Person", "RelatedPerson", "Group", "HealthcareService"
        };

        for (String resourceType : resourceTypes) {
            CapabilityStatement.CapabilityStatementRestResourceComponent resourceComponent =
                    rest.addResource();
            resourceComponent.setType(resourceType);
            resourceComponent.addInteraction().setCode(CapabilityStatement.TypeRestfulInteraction.READ);
            resourceComponent.addInteraction().setCode(CapabilityStatement.TypeRestfulInteraction.VREAD);
            resourceComponent.addInteraction().setCode(CapabilityStatement.TypeRestfulInteraction.UPDATE);
            resourceComponent.addInteraction().setCode(CapabilityStatement.TypeRestfulInteraction.DELETE);
            resourceComponent.addInteraction().setCode(CapabilityStatement.TypeRestfulInteraction.HISTORYINSTANCE);
            resourceComponent.addInteraction().setCode(CapabilityStatement.TypeRestfulInteraction.HISTORYTYPE);
            resourceComponent.addInteraction().setCode(CapabilityStatement.TypeRestfulInteraction.CREATE);
            resourceComponent.addInteraction().setCode(CapabilityStatement.TypeRestfulInteraction.SEARCHTYPE);
            resourceComponent.setVersioning(CapabilityStatement.ResourceVersionPolicy.VERSIONED);
            resourceComponent.setReadHistory(true);
            resourceComponent.setUpdateCreate(true);
            resourceComponent.setConditionalCreate(false);
            resourceComponent.setConditionalUpdate(false);
            resourceComponent.setConditionalDelete(CapabilityStatement.ConditionalDeleteStatus.NOTSUPPORTED);

            resourceComponent.addSearchParam()
                    .setName("_id")
                    .setType(Enumerations.SearchParamType.TOKEN)
                    .setDocumentation("Resource ID");
            resourceComponent.addSearchParam()
                    .setName("_lastUpdated")
                    .setType(Enumerations.SearchParamType.DATE)
                    .setDocumentation("Last updated date");
        }

        rest.addInteraction().setCode(CapabilityStatement.SystemRestfulInteraction.TRANSACTION);
        rest.addInteraction().setCode(CapabilityStatement.SystemRestfulInteraction.BATCH);
        rest.addInteraction().setCode(CapabilityStatement.SystemRestfulInteraction.HISTORYSYSTEM);
        rest.addInteraction().setCode(CapabilityStatement.SystemRestfulInteraction.SEARCHSYSTEM);

        return cs;
    }

    public long getResourceCount(String resourceType) {
        return resourceRepository.countByResourceTypeAndDeletedFalse(resourceType);
    }

    public boolean resourceExists(String resourceType, String resourceId) {
        return resourceRepository.existsByResourceTypeAndResourceId(resourceType, resourceId);
    }

    private Bundle.BundleEntryComponent processTransactionEntry(
            Bundle.BundleEntryComponent entry,
            Map<String, String> idMappings) {

        Bundle.BundleEntryComponent responseEntry = new Bundle.BundleEntryComponent();
        Bundle.BundleEntryResponseComponent response = new Bundle.BundleEntryResponseComponent();

        try {
            Bundle.BundleEntryRequestComponent request = entry.getRequest();
            if (request == null) {
                throw new FhirValidationException("Transaction entry must have a request");
            }

            Bundle.HTTPVerb method = request.getMethod();
            String url = request.getUrl();

            switch (method) {
                case POST -> {
                    Resource resource = entry.getResource();
                    Resource created = (Resource) create(resource);
                    String resourceType = fhirContext.getResourceType(created);
                    response.setStatus("201 Created");
                    response.setLocation(baseUrl + "/" + resourceType + "/" + created.getIdElement().getIdPart());
                    response.setEtag("W/\"1\"");
                    response.setLastModified(created.getMeta().getLastUpdated());
                    if (entry.getFullUrl() != null) {
                        idMappings.put(entry.getFullUrl(), resourceType + "/" + created.getIdElement().getIdPart());
                    }
                }
                case PUT -> {
                    String[] urlParts = url.split("/");
                    String resourceType = urlParts[0];
                    String resourceId = urlParts.length > 1 ? urlParts[1] : null;
                    Resource resource = entry.getResource();
                    Resource updated = (Resource) update(resourceType, resourceId, resource);
                    response.setStatus("200 OK");
                    response.setEtag("W/\"" + updated.getMeta().getVersionId() + "\"");
                    response.setLastModified(updated.getMeta().getLastUpdated());
                }
                case DELETE -> {
                    String[] urlParts = url.split("/");
                    String resourceType = urlParts[0];
                    String resourceId = urlParts[1];
                    delete(resourceType, resourceId);
                    response.setStatus("204 No Content");
                }
                case GET -> {
                    String[] urlParts = url.split("/");
                    String resourceType = urlParts[0];
                    String resourceId = urlParts[1];
                    IBaseResource resource = read(resourceType, resourceId);
                    responseEntry.setResource((Resource) resource);
                    response.setStatus("200 OK");
                }
                default -> throw new FhirValidationException("Unsupported HTTP method: " + method);
            }
        } catch (Exception e) {
            response.setStatus("400 Bad Request");
            OperationOutcome outcome = new OperationOutcome();
            outcome.addIssue()
                    .setSeverity(OperationOutcome.IssueSeverity.ERROR)
                    .setCode(OperationOutcome.IssueType.PROCESSING)
                    .setDiagnostics(e.getMessage());
            responseEntry.setResource(outcome);
        }

        responseEntry.setResponse(response);
        return responseEntry;
    }

    private void saveHistory(String resourceType, String resourceId, Long versionId, String resourceJson, String action) {
        FhirResourceHistory history = FhirResourceHistory.builder()
                .resourceType(resourceType)
                .resourceId(resourceId)
                .versionId(versionId)
                .resourceJson(resourceJson)
                .timestamp(Instant.now())
                .action(action)
                .build();
        historyRepository.save(history);
    }

    private Bundle createSearchBundle(Page<FhirResourceDocument> results, String resourceType,
                                       Map<String, String> searchParams, int page, int pageSize) {
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.SEARCHSET);
        bundle.setTotal((int) results.getTotalElements());
        bundle.setTimestamp(Date.from(Instant.now()));

        for (FhirResourceDocument doc : results.getContent()) {
            Bundle.BundleEntryComponent entry = bundle.addEntry();
            entry.setFullUrl(baseUrl + "/" + doc.getResourceType() + "/" + doc.getResourceId());

            IBaseResource resource = parseResource(doc.getResourceJson());
            entry.setResource((Resource) resource);

            Bundle.BundleEntrySearchComponent search = new Bundle.BundleEntrySearchComponent();
            search.setMode(Bundle.SearchEntryMode.MATCH);
            entry.setSearch(search);
        }

        addPaginationLinks(bundle, resourceType, page, pageSize, results.getTotalPages());

        return bundle;
    }

    private Bundle createSystemSearchBundle(Page<FhirResourceDocument> results,
                                             Map<String, String> searchParams, int page, int pageSize) {
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.SEARCHSET);
        bundle.setTotal((int) results.getTotalElements());
        bundle.setTimestamp(Date.from(Instant.now()));

        for (FhirResourceDocument doc : results.getContent()) {
            Bundle.BundleEntryComponent entry = bundle.addEntry();
            entry.setFullUrl(baseUrl + "/" + doc.getResourceType() + "/" + doc.getResourceId());

            IBaseResource resource = parseResource(doc.getResourceJson());
            entry.setResource((Resource) resource);
        }

        addPaginationLinks(bundle, "", page, pageSize, results.getTotalPages());

        return bundle;
    }

    private void addPaginationLinks(Bundle bundle, String path, int page, int pageSize, int totalPages) {
        String baseLink = baseUrl + "/" + path + "?_count=" + pageSize;

        bundle.addLink().setRelation("self").setUrl(baseLink + "&_page=" + page);

        if (page > 0) {
            bundle.addLink().setRelation("first").setUrl(baseLink + "&_page=0");
            bundle.addLink().setRelation("previous").setUrl(baseLink + "&_page=" + (page - 1));
        }

        if (page < totalPages - 1) {
            bundle.addLink().setRelation("next").setUrl(baseLink + "&_page=" + (page + 1));
            bundle.addLink().setRelation("last").setUrl(baseLink + "&_page=" + (totalPages - 1));
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends IBaseResource> T parseResource(String resourceJson) {
        return (T) fhirContext.newJsonParser().parseResource(resourceJson);
    }

    private String generateResourceId() {
        return UUID.randomUUID().toString();
    }

    private Bundle.HTTPVerb getHttpMethod(String action) {
        return switch (action) {
            case "CREATE" -> Bundle.HTTPVerb.POST;
            case "UPDATE" -> Bundle.HTTPVerb.PUT;
            case "DELETE" -> Bundle.HTTPVerb.DELETE;
            default -> Bundle.HTTPVerb.GET;
        };
    }
}
