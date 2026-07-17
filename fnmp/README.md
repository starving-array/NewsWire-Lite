# FNMP - Financial News Management Platform

A production-grade platform for ingesting, storing, searching, and serving financial news articles.

## Architecture

- **article-service** (port 8080) — CRUD API, PostgreSQL persistence, Redis caching, full-text search
- **search-service** (port 8081) — OpenSearch-backed search with circuit-breaker fallback to PostgreSQL
- **ingestion-service** (port 8082) — Kafka-based event ingestion for high-throughput article submission
- **common** — Shared domain models, DTOs, events, and configuration

## Quick Start

```bash
# Start infrastructure
docker compose -f infra/docker-compose.yml up -d postgres redis kafka opensearch

# Build and run
git clone https://github.com/starving-array/NewsWire-Lite.git
cd NewsWire-Lite/fnmp
mvn spring-boot:run -pl article-service
```

## Documentation

| Document | Location |
|---|---|
| Architecture | `ARCHITECTURE.md` |
| Developer Guide | `DEVELOPER_GUIDE.md` |
| ER Diagram | `docs/er-diagram.md` |
| Security Checklist | `docs/security/SECURITY_AUDIT_CHECKLIST.md` |
| DR Runbook | `docs/ops/DR_RUNBOOK.md` |
| Load Test Scripts | `infra/k6/` |
| Chaos Scenarios | `infra/chaos/` |
| Helm Chart | `infra/helm/fnmp/` |

## API

OpenAPI docs available at `/swagger-ui.html` when running any service.
