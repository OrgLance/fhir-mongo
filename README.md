# FHIR R4 Server

A high-performance FHIR R4 server built with Spring Boot and MongoDB, optimized for handling billions of records.

## Features

- Full FHIR R4 compliance with 145 resource types
- MongoDB backend with optimized indexes
- Redis caching for high-performance reads
- Cursor-based pagination for efficient large dataset handling
- Async processing for non-blocking operations
- GZIP compression for large resources
- Prometheus metrics for monitoring
- Swagger/OpenAPI documentation
- Docker support with full stack deployment

## Quick Start

### Prerequisites

- Docker & Docker Compose
- Java 17+ (for local development)
- Maven 3.8+ (for local development)

### Start with Docker (Recommended)

```bash
# Clone the repository
git clone <repository-url>
cd fhirSpringboot

# Start all services
./scripts/start.sh

# Or with all admin & monitoring tools
./scripts/start.sh --all

# Check status
./scripts/status.sh

# View logs
./scripts/logs.sh
```

### Start Locally

```bash
# Start MongoDB and Redis only
docker-compose up -d mongodb redis

# Run the application
mvn spring-boot:run
```

---

## Shell Scripts

All scripts are located in the `scripts/` directory:

| Script | Description |
|--------|-------------|
| `start.sh` | Start services with various profiles |
| `stop.sh` | Stop services (optionally remove data) |
| `status.sh` | Show status of all services |
| `logs.sh` | View service logs |
| `restart.sh` | Restart services |
| `build.sh` | Build Maven project and Docker image |

### Start Script

```bash
# Basic start (MongoDB + Redis + FHIR Server)
./scripts/start.sh

# Start with admin UIs
./scripts/start.sh --admin

# Start with monitoring (Prometheus + Grafana)
./scripts/start.sh --monitoring

# Start all services
./scripts/start.sh --all

# Rebuild and start
./scripts/start.sh --all --build

# Run in foreground (see logs)
./scripts/start.sh --foreground
```

### Stop Script

```bash
# Stop services (keep data)
./scripts/stop.sh

# Stop and remove all data
./scripts/stop.sh --volumes

# Complete cleanup
./scripts/stop.sh --all
```

### Status Script

```bash
# Show status of all services
./scripts/status.sh
```

### Logs Script

```bash
# Follow all logs
./scripts/logs.sh

# Follow FHIR server logs
./scripts/logs.sh fhir

# Show last 50 lines of MongoDB logs
./scripts/logs.sh mongo -n 50

# Show logs without following
./scripts/logs.sh --no-follow
```

### Restart Script

```bash
# Restart all services
./scripts/restart.sh

# Restart only FHIR server
./scripts/restart.sh fhir

# Rebuild and restart
./scripts/restart.sh fhir --build
```

### Build Script

```bash
# Full build (Maven + Docker)
./scripts/build.sh

# Build without tests
./scripts/build.sh --skip-tests

# Clean build
./scripts/build.sh --clean

# Maven only
./scripts/build.sh --maven-only

# Docker only
./scripts/build.sh --docker-only
```

## Access URLs

| Service | URL | Description |
|---------|-----|-------------|
| FHIR Server | http://localhost:8080/fhir | FHIR API endpoint |
| Swagger UI | http://localhost:8080/swagger-ui.html | API documentation |
| OpenAPI Spec | http://localhost:8080/api-docs | OpenAPI JSON |
| Health Check | http://localhost:8080/actuator/health | Application health |
| Metrics | http://localhost:8080/actuator/prometheus | Prometheus metrics |

---

## Credentials

### MongoDB

| Property | Value |
|----------|-------|
| Host | `localhost` (local) / `mongodb` (docker) |
| Port | `27017` |
| Database | `fhirdb` |
| Username | `fhiruser` |
| Password | `fhirpass` |
| Connection String | `mongodb://fhiruser:fhirpass@localhost:27017/fhirdb?authSource=admin` |

### Redis (Secured)

| Property | Value |
|----------|-------|
| Host | `localhost` (local) / `redis` (docker) |
| Port | `6379` |
| Password | `fhirRedis@2024` |
| Connection | `redis://:fhirRedis@2024@localhost:6379` |

### MongoDB Express (Admin UI)

| Property | Value |
|----------|-------|
| URL | http://localhost:8081 |
| Username | `admin` |
| Password | `admin123` |

### Redis Commander (Admin UI)

| Property | Value |
|----------|-------|
| URL | http://localhost:8082 |
| Username | `admin` |
| Password | `admin123` |

### Prometheus

| Property | Value |
|----------|-------|
| URL | http://localhost:9090 |
| Authentication | *(none)* |

### Grafana

| Property | Value |
|----------|-------|
| URL | http://localhost:3000 |
| Username | `admin` |
| Password | `admin123` |

---

## Docker Compose Profiles

```bash
# Basic setup (MongoDB + Redis + FHIR Server)
docker-compose up -d

# With admin UIs (adds MongoDB Express + Redis Commander)
docker-compose --profile admin up -d

# With monitoring (adds Prometheus + Grafana)
docker-compose --profile monitoring up -d

# Full stack (all services)
docker-compose --profile admin --profile monitoring up -d
```

---

## API Examples

### Create a Patient

```bash
curl -X POST http://localhost:8080/fhir/Patient \
  -H "Content-Type: application/json" \
  -d '{
    "resourceType": "Patient",
    "name": [{"family": "Smith", "given": ["John"]}],
    "gender": "male",
    "birthDate": "1990-01-15"
  }'
```

### Read a Patient

```bash
curl http://localhost:8080/fhir/Patient/{id}
```

### Search Patients

```bash
# Offset-based pagination
curl "http://localhost:8080/fhir/Patient?_page=0&_count=20"

# Cursor-based pagination (for large datasets)
curl "http://localhost:8080/fhir/Patient?_cursor={lastId}&_count=20"
```

### Update a Patient

```bash
curl -X PUT http://localhost:8080/fhir/Patient/{id} \
  -H "Content-Type: application/json" \
  -d '{
    "resourceType": "Patient",
    "id": "{id}",
    "name": [{"family": "Smith", "given": ["John", "William"]}],
    "gender": "male",
    "birthDate": "1990-01-15"
  }'
```

### Delete a Patient

```bash
curl -X DELETE http://localhost:8080/fhir/Patient/{id}
```

### Get Server Capabilities

```bash
curl http://localhost:8080/fhir/metadata
```

### Transaction Bundle

```bash
curl -X POST http://localhost:8080/fhir \
  -H "Content-Type: application/json" \
  -d '{
    "resourceType": "Bundle",
    "type": "transaction",
    "entry": [
      {
        "fullUrl": "urn:uuid:patient-1",
        "resource": {
          "resourceType": "Patient",
          "name": [{"family": "Doe", "given": ["Jane"]}]
        },
        "request": {
          "method": "POST",
          "url": "Patient"
        }
      }
    ]
  }'
```

---

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `MONGODB_URI` | `mongodb://fhiruser:fhirpass@localhost:27017/fhirdb?authSource=admin` | MongoDB connection string |
| `MONGODB_DATABASE` | `fhirdb` | Database name |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_PASSWORD` | *(empty)* | Redis password |
| `FHIR_BASE_URL` | `http://localhost:8080/fhir` | Base URL for FHIR resources |
| `MONGO_POOL_MAX_SIZE` | `200` | Max MongoDB connections |
| `MONGO_POOL_MIN_SIZE` | `20` | Min MongoDB connections |
| `ASYNC_CORE_POOL_SIZE` | `10` | Async thread pool core size |
| `ASYNC_MAX_POOL_SIZE` | `50` | Async thread pool max size |
| `BATCH_SIZE` | `1000` | Batch processing size |

### Application Properties

Key configuration in `application.yml`:

```yaml
fhir:
  server:
    base-url: http://localhost:8080/fhir
    default-page-size: 20
    max-page-size: 100
  cache:
    default-ttl-minutes: 60
    resource-ttl-minutes: 30
    search-ttl-minutes: 5
  compression:
    enabled: true
    threshold-bytes: 10000
```

---

## Supported FHIR Resources

This server supports all 145 FHIR R4 resource types including:

**Clinical:**
Patient, Practitioner, Organization, Encounter, Condition, Observation, DiagnosticReport, Procedure, MedicationRequest, AllergyIntolerance, Immunization, CarePlan, and more.

**Financial:**
Claim, ClaimResponse, Coverage, ExplanationOfBenefit, Invoice, PaymentNotice, and more.

**Workflow:**
Appointment, Schedule, Slot, Task, ServiceRequest, and more.

**Conformance:**
CapabilityStatement, StructureDefinition, ValueSet, CodeSystem, and more.

See `/fhir/metadata` for the complete list.

---

## Performance Optimizations

### Database Indexes

The server creates optimized compound indexes for:
- Resource lookups by type and ID
- Filtered queries on `deleted` flag
- Time-based sorting by `lastUpdated`
- Patient reference searches
- Code/coding lookups
- Full-text search

### Caching Strategy

| Cache | TTL | Purpose |
|-------|-----|---------|
| `resources` | 30 min | Individual resource reads |
| `searches` | 5 min | Search results |
| `metadata` | 24 hours | CapabilityStatement |
| `counts` | 10 min | Resource counts |
| `terminology` | 12 hours | ValueSet/CodeSystem |

### Pagination

- **Offset-based**: Traditional `_page` parameter (for small datasets)
- **Cursor-based**: `_cursor` parameter (O(1) performance for billions of records)

---

## Audit Logging

All FHIR resource operations are logged to MongoDB **Time Series Collections** for efficient time-based queries and automatic data expiration.

### Features

- **Per-resource collections**: Each resource type has its own time series collection (e.g., `audit_patient`, `audit_observation`)
- **Auto-creation**: Collections are created automatically on first use
- **Change tracking**: Stores old/new values and field-level diffs for UPDATE operations
- **Auto-expiration**: Data automatically expires after retention period (default: 90 days)
- **Async logging**: Non-blocking audit writes for optimal API performance

### Configuration

```yaml
fhir:
  audit:
    enabled: true              # Enable/disable audit logging
    retention-days: 90         # Auto-expire data after N days
    granularity: SECONDS       # Time series granularity (SECONDS, MINUTES, HOURS)
```

### Audit Log Structure

| Field | Description |
|-------|-------------|
| `timestamp` | Event timestamp (time series time field) |
| `action` | CREATE, READ, UPDATE, DELETE, SEARCH, etc. |
| `resourceType` | FHIR resource type |
| `resourceId` | Resource ID |
| `versionId` | Resource version after operation |
| `oldValue` | Previous state (UPDATE/DELETE) |
| `newValue` | New state (CREATE/UPDATE) |
| `changes` | Field-level diff (UPDATE only) |
| `actor` | User/system performing action |
| `durationMs` | Operation duration in milliseconds |

### Audit API Endpoints

```bash
# Get audit system status
curl http://localhost:8080/fhir/_audit/status

# Get collection info for a resource type
curl http://localhost:8080/fhir/_audit/collections/Patient

# Get audit logs for a specific resource
curl http://localhost:8080/fhir/_audit/Patient/p-123

# Get recent audit logs (last 24 hours)
curl http://localhost:8080/fhir/_audit/Patient/p-123/recent?hours=24

# Get audit statistics by action type
curl http://localhost:8080/fhir/_audit/Patient/stats
```

### Example Audit Log (UPDATE)

```json
{
  "timestamp": "2026-02-06T08:30:00Z",
  "action": "UPDATE",
  "resourceType": "Patient",
  "resourceId": "p-123",
  "versionId": 2,
  "oldValue": "{\"resourceType\":\"Patient\",\"gender\":\"male\"...}",
  "newValue": "{\"resourceType\":\"Patient\",\"gender\":\"female\"...}",
  "changes": {
    "gender": {
      "field": "gender",
      "oldValue": "male",
      "newValue": "female",
      "changeType": "MODIFIED"
    }
  },
  "durationMs": 45
}
```

---

## Monitoring

### Health Endpoints

```bash
# Application health
curl http://localhost:8080/actuator/health

# Detailed health (when authorized)
curl http://localhost:8080/actuator/health -u admin:admin
```

### Prometheus Metrics

Available at `/actuator/prometheus`:

- `fhir_resource_operations_total` - CRUD operation counts
- `fhir_resource_latency_seconds` - Operation latency (p50, p95, p99)
- `fhir_search_operations_total` - Search counts
- `fhir_cache_operations_total` - Cache hit/miss rates
- `fhir_bulk_operations_total` - Batch operation counts

### Grafana Dashboard

1. Start with monitoring profile: `docker-compose --profile monitoring up -d`
2. Access Grafana at http://localhost:3000
3. Login with `admin` / `admin123`
4. Add Prometheus data source: `http://prometheus:9090`
5. Import dashboard or create custom panels

---

## Development

### Build

```bash
mvn clean package
```

### Run Tests

```bash
mvn test
```

### Build Docker Image

```bash
docker build -t fhir-server:latest .
```

### Code Structure

```
src/main/java/com/fhir/
├── config/           # Configuration classes
│   ├── AsyncConfig.java
│   ├── CacheConfig.java
│   ├── FhirConfig.java
│   ├── MongoConfig.java
│   └── OpenApiConfig.java
├── controller/       # REST controllers
│   ├── FhirResourceController.java
│   ├── FhirSystemController.java
│   ├── PatientController.java
│   └── ...
├── exception/        # Exception handling
├── metrics/          # Prometheus metrics
├── model/            # Data models
├── repository/       # MongoDB repositories
├── service/          # Business logic
│   ├── FhirResourceService.java
│   ├── FhirBatchService.java
│   └── FhirSearchService.java
└── util/             # Utilities
```

---

## Scaling for Production

### MongoDB Sharding

For billions of records, enable MongoDB sharding:

```javascript
// Connect to mongos
sh.enableSharding("fhirdb")

// Shard by resourceType + resourceId
sh.shardCollection(
    "fhirdb.fhir_resources",
    { "resourceType": 1, "resourceId": "hashed" }
)
```

### Redis Cluster

For high availability, use Redis Cluster or Redis Sentinel.

### Horizontal Scaling

Deploy multiple FHIR server instances behind a load balancer:

```yaml
# docker-compose.override.yml
services:
  fhir-server:
    deploy:
      replicas: 3
```

---

## Troubleshooting

### Common Issues

**Connection refused to MongoDB:**
```bash
# Check if MongoDB is running
docker-compose ps mongodb
docker-compose logs mongodb
```

**Redis connection failed:**
```bash
# Check Redis status
docker-compose ps redis
redis-cli -h localhost ping
```

**Out of memory:**
```bash
# Increase JVM heap
JAVA_OPTS="-Xmx4g -Xms2g" mvn spring-boot:run
```

### Logs

```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f fhir-server

# Application logs
docker-compose exec fhir-server tail -f /app/logs/application.log
```

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support

For issues and feature requests, please open a GitHub issue.
