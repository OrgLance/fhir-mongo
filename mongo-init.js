// MongoDB initialization script
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
        }
    ]
});

// Create indexes for the fhir_resources collection
db.fhir_resources.createIndex(
    { "resourceType": 1, "resourceId": 1 },
    { unique: true, name: "resource_lookup" }
);

db.fhir_resources.createIndex(
    { "resourceType": 1, "lastUpdated": -1 },
    { name: "resource_type_updated" }
);

db.fhir_resources.createIndex(
    { "resourceType": 1, "deleted": 1 },
    { name: "resource_type_deleted" }
);

db.fhir_resources.createIndex(
    { "resourceId": 1 },
    { name: "resource_id" }
);

db.fhir_resources.createIndex(
    { "lastUpdated": -1 },
    { name: "last_updated" }
);

// Create indexes for the fhir_resource_history collection
db.fhir_resource_history.createIndex(
    { "resourceType": 1, "resourceId": 1, "versionId": -1 },
    { name: "history_lookup" }
);

db.fhir_resource_history.createIndex(
    { "resourceType": 1, "resourceId": 1, "timestamp": -1 },
    { name: "history_timestamp" }
);

print("MongoDB initialization completed successfully!");
