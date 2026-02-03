// MongoDB initialization script for High-Performance FHIR Server
// This script runs when the MongoDB container starts for the first time

// Switch to the fhirdb database
db = db.getSiblingDB('fhirdb');

// Create the application user with readWrite permissions
db.createUser({
    user: 'fhiruser',
    pwd: 'fhirpass',
    roles: [
        {
            role: 'readWrite',
            db: 'fhirdb'
        },
        {
            role: 'dbAdmin',
            db: 'fhirdb'
        }
    ]
});

print("Creating indexes for fhir_resources collection...");

// ============ FHIR RESOURCES COLLECTION INDEXES ============

// Primary lookup - unique resource identifier
db.fhir_resources.createIndex(
    { "resourceType": 1, "resourceId": 1 },
    { unique: true, name: "resource_lookup", background: true }
);

// CRITICAL: Search queries with deleted filter (most common query pattern)
db.fhir_resources.createIndex(
    { "resourceType": 1, "deleted": 1 },
    { name: "resource_type_deleted", background: true }
);

// Search with sorting by lastUpdated (covers 90% of search queries)
db.fhir_resources.createIndex(
    { "resourceType": 1, "deleted": 1, "lastUpdated": -1 },
    { name: "resource_type_deleted_updated", background: true }
);

// Active resources query optimization
db.fhir_resources.createIndex(
    { "resourceType": 1, "active": 1, "lastUpdated": -1 },
    { name: "resource_active_updated", background: true }
);

// Cursor-based pagination support (O(1) performance)
db.fhir_resources.createIndex(
    { "resourceType": 1, "deleted": 1, "_id": 1 },
    { name: "cursor_pagination", background: true }
);

// Patient reference lookup (common FHIR query pattern)
db.fhir_resources.createIndex(
    { "resourceType": 1, "resourceData.subject.reference": 1, "deleted": 1 },
    { name: "patient_subject_reference", sparse: true, background: true }
);

db.fhir_resources.createIndex(
    { "resourceType": 1, "resourceData.patient.reference": 1, "deleted": 1 },
    { name: "patient_reference", sparse: true, background: true }
);

// Code/coding lookup (Observation, Condition queries)
db.fhir_resources.createIndex(
    { "resourceType": 1, "resourceData.code.coding.system": 1, "resourceData.code.coding.code": 1, "deleted": 1 },
    { name: "coding_lookup", sparse: true, background: true }
);

// Identifier lookup (common for patient matching)
db.fhir_resources.createIndex(
    { "resourceType": 1, "resourceData.identifier.system": 1, "resourceData.identifier.value": 1, "deleted": 1 },
    { name: "identifier_lookup", sparse: true, background: true }
);

// Status-based queries
db.fhir_resources.createIndex(
    { "resourceType": 1, "resourceData.status": 1, "deleted": 1, "lastUpdated": -1 },
    { name: "status_lookup", sparse: true, background: true }
);

// Single field indexes for simple queries
db.fhir_resources.createIndex(
    { "resourceId": 1 },
    { name: "resource_id", background: true }
);

db.fhir_resources.createIndex(
    { "lastUpdated": -1 },
    { name: "last_updated", background: true }
);

db.fhir_resources.createIndex(
    { "deleted": 1 },
    { name: "deleted", background: true }
);

// Text index for full-text search
db.fhir_resources.createIndex(
    { "resourceJson": "text" },
    { name: "fulltext_search", background: true }
);

print("Created " + db.fhir_resources.getIndexes().length + " indexes on fhir_resources");

// ============ FHIR RESOURCE HISTORY COLLECTION INDEXES ============

print("Creating indexes for fhir_resource_history collection...");

// Primary history lookup
db.fhir_resource_history.createIndex(
    { "resourceType": 1, "resourceId": 1, "versionId": -1 },
    { name: "history_lookup", background: true }
);

// Timestamp-based queries
db.fhir_resource_history.createIndex(
    { "resourceType": 1, "resourceId": 1, "timestamp": -1 },
    { name: "history_timestamp", background: true }
);

// Type-level history queries
db.fhir_resource_history.createIndex(
    { "resourceType": 1, "timestamp": -1 },
    { name: "type_history", background: true }
);

// Action-based queries (for audit)
db.fhir_resource_history.createIndex(
    { "resourceType": 1, "action": 1, "timestamp": -1 },
    { name: "action_lookup", background: true }
);

// TTL index for automatic cleanup (2 years = 63072000 seconds)
// Uncomment to enable automatic history cleanup
// db.fhir_resource_history.createIndex(
//     { "timestamp": 1 },
//     { name: "history_ttl", expireAfterSeconds: 63072000, background: true }
// );

print("Created " + db.fhir_resource_history.getIndexes().length + " indexes on fhir_resource_history");

// ============ COLLECTION STATISTICS ============

print("\n========== INDEX SUMMARY ==========");
print("fhir_resources indexes:");
db.fhir_resources.getIndexes().forEach(function(idx) {
    print("  - " + idx.name + ": " + JSON.stringify(idx.key));
});

print("\nfhir_resource_history indexes:");
db.fhir_resource_history.getIndexes().forEach(function(idx) {
    print("  - " + idx.name + ": " + JSON.stringify(idx.key));
});

print("\n========================================");
print("MongoDB initialization completed successfully!");
print("========================================\n");
