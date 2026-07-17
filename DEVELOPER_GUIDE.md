# FNMP — Developer Guide

## 1. Project Structure

```
NewsWire-Lite/
├── .gitignore
├── LICENSE                       # Apache 2.0
├── README.md                     # Project overview
├── ARCHITECTURE.md               # Architecture deep-dive (diagrams, DB, trade-offs)
├── DEVELOPER_GUIDE.md            # ← you are here
├── maven-dist/                   # Portable Maven distribution (extracted from Docker)
└── fnmp/                         # Multi-module Maven project
    ├── pom.xml                   # Parent POM (Spring Boot 3.3.13, Java 17)
    ├── common/                   # Shared library — DTOs, events, enums, config
    │   └── src/main/java/com/fnmp/common/
    │       ├── domain/           #   ArticleCategory, ArticleStatus, ReliabilityTier
    │       ├── dto/              #   CreateArticleRequest
    │       ├── event/            #   ArticleCreatedEvent, ArticleDeletedEvent
    │       └── config/           #   KafkaTopicConfig, TracingConfig
    ├── article-service/          # Core CRUD API (port 8080)
    │   ├── Dockerfile
    │   └── src/main/java/com/fnmp/article/
    │       ├── controller/       #   ArticleController (REST endpoints)
    │       ├── service/          #   ArticleService (business logic, @Transactional)
    │       ├── repository/       #   Spring Data JPA repositories
    │       ├── domain/           #   Article, ArticleId, ArticleTag, Source, Tag, Audit
    │       ├── dto/              #   ArticleResponse, ArticleSummaryResponse, PagedResponse
    │       ├── mapper/           #   ArticleMapper (MapStruct)
    │       ├── exception/        #   GlobalExceptionHandler + custom exceptions
    │       ├── cache/            #   CacheService (Redis)
    │       ├── specification/    #   ArticleSpecifications (JPA Criteria)
    │       ├── consumer/         #   Kafka consumer (ArticleCreatedConsumer)
    │       └── publisher/        #   Kafka publisher (ArticleEventPublisher)
    ├── ingestion-service/        # Ingestion API (port 8082)
    │   └── src/main/java/com/fnmp/ingestion/
    │       ├── controller/       #   IngestionController
    │       └── config/           #   SecurityConfig
    ├── search-service/           # OpenSearch-backed search (port 8081)
    │   └── src/main/java/com/fnmp/search/
    │       ├── controller/       #   SearchController
    │       ├── service/          #   OpenSearchService, SearchService
    │       ├── consumer/         #   ArticleIndexerConsumer (Kafka → OpenSearch)
    │       └── config/           #   OpenSearchConfig, OpenSearchIndexInitializer
    ├── infra/
    │   ├── docker-compose.yml    # Postgres, Redis, Kafka, OpenSearch + monitoring stack
    │   ├── helm/fnmp/            # Kubernetes Helm chart
    │   ├── k6/                   # Load test scripts
    │   ├── chaos/                # Chaos engineering scripts
    │   ├── prometheus/           # Prometheus config + alert rules
    │   ├── grafana/              # Grafana dashboards + datasources
    │   ├── loki/                 # Loki config
    │   ├── tempo/                # Tempo (tracing) config
    │   └── otel/                 # OpenTelemetry Collector config
    ├── docs/
    │   ├── er-diagram.md
    │   ├── ops/                  # Runbooks, DR plans
    │   └── security/             # Security audit checklist
    └── .github/workflows/        # CI + staging-deploy pipelines
```

---

## 2. Architecture Overview

FNMP is a **modular monolith** designed for future extraction into independent microservices.

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│  Ingestion   │     │   Article    │     │   Search     │
│  Service     │────>│   Service    │────>│   Service    │
│  (port 8082) │  K  │  (port 8080) │  K  │  (port 8081) │
└──────────────┘  A  └──────┬───────┘  A  └──────┬───────┘
                    F       │                F    │
                    K       │                K    │
                    A       ▼                A    ▼
                          ┌───────┐           ┌──────────┐
                          │Postgre│           │OpenSearch│
                          │ SQL   │           │          │
                          └──┬────┘           └──────────┘
                             │
                          ┌──▼───┐
                          │Redis │
                          └──────┘
```

- **Ingestion Service**: Validates incoming article submissions, publishes to Kafka. Never touches the database directly.
- **Article Service**: The core service. Consumes Kafka events → persists to PostgreSQL; serves CRUD REST API with Redis caching; performs full-text search via PostgreSQL GIN index (fallback when OpenSearch is unavailable).
- **Search Service**: Consumes Kafka events → indexes into OpenSearch; serves search queries with circuit-breaker fallback to the Article Service's PostgreSQL full-text endpoint.

Full architecture details, including C4-style diagrams, database schema, index strategy, partitioning, scalability, reliability, observability, and security analysis are in [`ARCHITECTURE.md`](./ARCHITECTURE.md).

---

## 3. Local Run Guide

### 3.1 Prerequisites

- **Java 17** (Eclipse Adoptium / Temurin recommended)
- **Maven 3.9+** (a portable Maven distribution is included at `maven-dist/`)
- **Docker** + **Docker Compose** (for PostgreSQL, Redis, Kafka, OpenSearch)

### 3.2 Start Infrastructure

```bash
cd fnmp
docker compose -f infra/docker-compose.yml up -d postgres redis kafka opensearch
```

This starts PostgreSQL (5432), Redis (6379), Kafka (9092), and OpenSearch (9200).

> To also start the monitoring stack (Prometheus, Grafana, Loki, Tempo, OTEL Collector):
> ```bash
> docker compose -f infra/docker-compose.yml --profile monitoring up -d
> ```

### 3.3 Build and Run Article Service

```bash
# Build the project (skip tests for faster development turnaround)
mvn package -pl article-service -am -DskipTests

# Run the JAR (adjust env vars as needed)
java -jar article-service/target/article-service-0.0.1-SNAPSHOT.jar
```

The service starts on **http://localhost:8080**.

> **Troubleshooting — health group errors:**
> If the readiness group references `kafka` or `circuitbreakers` that are not available, override it:
> ```bash
> java -jar article-service/target/article-service-0.0.1-SNAPSHOT.jar \
>   --management.endpoint.health.group.readiness.include=db,redis
> ```

### 3.4 Verify the Service is Alive

```bash
curl http://localhost:8080/actuator/health
```

Expected response:
```json
{"status":"UP","groups":["liveness","readiness"]}
```

### 3.5 API Walkthrough with curl

#### Create an Article

```bash
curl -s -X POST http://localhost:8080/api/v1/articles \
  -H "Content-Type: application/json" \
  -d '{
    "headline": "Fed holds rates steady in July meeting",
    "summary": "The Federal Reserve kept interest rates unchanged as expected.",
    "body": "The Federal Reserve announced today that it would hold interest rates steady...",
    "source": "Reuters",
    "publicationTimestamp": "2026-07-17T15:00:00Z",
    "category": "MONETARY_POLICY",
    "tags": ["fed", "interest-rates"]
  }' | jq .
```

Expected response — **201 Created**:
```json
{
  "id": "b3f1c2a4-...",
  "headline": "Fed holds rates steady in July meeting",
  "source": "Reuters",
  "publicationTimestamp": "2026-07-17T15:00:00Z",
  "createdAt": "2026-07-17T15:00:03Z",
  "status": "PUBLISHED",
  "tags": ["fed", "interest-rates"]
}
```

Save the returned `id` for subsequent requests.

#### Get an Article by ID

```bash
curl -s http://localhost:8080/api/v1/articles/b3f1c2a4-... | jq .
```

Expected — **200 OK** with the full article body.

#### List Articles (paginated)

```bash
curl -s "http://localhost:8080/api/v1/articles?size=10&sort=id.publicationTimestamp,desc" | jq .
```

Expected — **200 OK** with a `PagedResponse` containing `content`, `page`, `size`, `totalElements`, `totalPages`.

**With filters:**

```bash
curl -s "http://localhost:8080/api/v1/articles?category=MONETARY_POLICY&source=Reuters&size=5" | jq .
```

**With cursor-based pagination (for deep pages):**

```bash
curl -s "http://localhost:8080/api/v1/articles?afterId=<article-uuid>&afterPubTs=<publication-timestamp>&size=10" | jq .
```

#### Search Articles

```bash
curl -s "http://localhost:8080/api/v1/articles/search?q=Federal+Reserve&limit=10" | jq .
```

Expected — **200 OK** with an array of matching article summaries, ranked by relevance.

#### Soft-Delete an Article

```bash
curl -s -X DELETE http://localhost:8080/api/v1/articles/b3f1c2a4-...
```

Expected — **204 No Content**. Subsequent GET will return the article with `status: "RETRACTED"`.

#### Error Responses

**Validation error (400):**
```bash
curl -s -X POST http://localhost:8080/api/v1/articles \
  -H "Content-Type: application/json" \
  -d '{"headline": "", "source": "Reuters", "publicationTimestamp": "2026-07-17T15:00:00Z"}' | jq .
```

Expected — **400 Bad Request** with RFC 7807 `application/problem+json`:
```json
{
  "type": "https://fnmp.dev/errors/validation-error",
  "title": "Validation Failed",
  "status": 400,
  "detail": "headline: must not be blank"
}
```

**Not found (404):**
```bash
curl -s http://localhost:8080/api/v1/articles/00000000-0000-0000-0000-000000000000 | jq .
```

Expected — **404 Not Found**:
```json
{
  "type": "https://fnmp.dev/errors/article-not-found",
  "title": "Article Not Found",
  "status": 404,
  "detail": "Article not found: 00000000-...",
  "instance": "/api/v1/articles/00000000-..."
}
```

---

## 4. Swagger / OpenAPI Docs

The `springdoc-openapi-starter-webmvc-ui` library is included in all three services.

### Accessing Swagger UI

With the Article Service running:

| URL | Description |
|---|---|
| `http://localhost:8080/swagger-ui.html` | Interactive Swagger UI — browse and test endpoints |
| `http://localhost:8080/v3/api-docs` | OpenAPI 3.0 spec in JSON |
| `http://localhost:8080/v3/api-docs.yaml` | OpenAPI 3.0 spec in YAML |

### What's Documented

Swagger auto-discovers all `@RestController` endpoints with their request/response schemas, validation constraints, and parameter descriptions — no custom annotations needed. The UI lets you:

- Browse all available endpoints grouped by controller
- View request DTO schemas with field descriptions and validation rules
- Execute requests directly from the browser (omit the JWT `Authorization` header in local dev)
- Download the OpenAPI spec for use with code generators or API clients

### Observing Request/Response Schemas

Request DTOs (e.g., `CreateArticleRequest`) are automatically reflected in Swagger, including:

- Required vs optional fields
- String length constraints (`@Size`, `@NotBlank`)
- Enum allowed values (`ArticleCategory`, `ArticleStatus`)
- Nested objects (e.g., `tags: string[]`)

Response DTOs (`ArticleResponse`, `ArticleSummaryResponse`, `PagedResponse`) are also documented, so consumers know exactly what to expect.

---

## 5. Testing

### 5.1 Run All Tests

```bash
mvn test -pl article-service -am
```

All **27 tests** must pass before merging:

| Test Class | Count | What it covers |
|---|---|---|
| `ArticleControllerTest` | 7 | CRUD endpoints, search, list, pagination, 404, validation rejection |
| `ValidationExceptionTest` | 11 | RFC 7807 error shapes, validation, type mismatch, not-found, unknown routes |
| `TracePropagationTest` | 2 | Request succeeds with trace context headers |
| `ArticleRepositoryTest` | 7 | JPA mappings, save/find, full-text search, date range, composite keys, JSONB audit |

### 5.2 Test Stack

- **JUnit 5** + **AssertJ** for test assertions
- **Testcontainers** (PostgreSQL 16 Alpine) for database integration tests — no H2
- **Spring Boot Test** (`@SpringBootTest`) with `TestRestTemplate` for end-to-end controller tests
- **`@MockBean StringRedisTemplate`** used in `@SpringBootTest` tests (Redis excluded in test profile)
- Flyway migrations run during tests with `ddl-auto: validate`

---

## 6. Build Commands

```bash
mvn compile                            # Compile all modules
mvn compile -pl article-service -am    # Compile article-service + dependencies
mvn test -pl article-service -am       # Test article-service only
mvn package -pl article-service -am    # Build JAR (skip tests with -DskipTests)
mvn package                           # Build all modules
```

---

## 7. Environment Variables

| Variable | Default | Description |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` | Active Spring profiles |
| `POSTGRES_HOST` | `localhost` | PostgreSQL host |
| `POSTGRES_USER` | `fnmp` | PostgreSQL user |
| `POSTGRES_PASSWORD` | `fnmp` | PostgreSQL password |
| `REDIS_HOST` | `localhost` | Redis host |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka bootstrap servers |
| `JWT_ISSUER_URI` | `http://localhost:8080/auth/realms/fnmp` | OAuth2 issuer |
| `TRACING_SAMPLING_PROBABILITY` | `0.1` | OpenTelemetry trace sampling rate |
