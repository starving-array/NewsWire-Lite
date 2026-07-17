# Financial News Management Platform (FNMP)

A production-grade backend platform for storing, searching, and serving financial news articles, economic announcements, and market events — built as a portfolio piece demonstrating production-level backend architecture, DevSecOps, and reliability engineering practices.

## Stack
Java 17 · Spring Boot 3.3 · PostgreSQL 16 · Redis 7 · Kafka 3.7 · OpenSearch 2.13 · Kubernetes · Prometheus/Grafana/Loki/OpenTelemetry

## What's in this repo/docs set
| Doc | Purpose |
|---|---|
| [`ARCHITECTURE.md`](./ARCHITECTURE.md) | High/low-level diagrams, DB design, API design, scalability, reliability, observability, security, bottleneck & trade-off analysis, documented assumptions |
| [`DEVELOPER_GUIDE.md`](./DEVELOPER_GUIDE.md) | Project structure, local run guide, API reference with curl samples, Swagger UI, architecture overview |

## Quick Start
```bash
git clone https://github.com/starving-array/NewsWire-Lite.git
cd NewsWire-Lite/fnmp

# Start infrastructure (PostgreSQL, Redis, Kafka, OpenSearch)
docker compose -f infra/docker-compose.yml up -d postgres redis kafka opensearch

# Build and run the article service
mvn spring-boot:run -pl article-service

# Verify it's alive
curl http://localhost:8080/actuator/health
```

**Requirements:** Java 17, Maven 3.9+, Docker, Docker Compose.

## Core Capabilities
- Create / retrieve (single + paginated list) / search / delete financial news articles
- Full-text + faceted search (OpenSearch, with PostgreSQL full-text as a graceful-degradation fallback)
- Kafka-based async ingestion pipeline decoupling write bursts from database throughput
- Redis caching for hot reads
- Structured logging, metrics, and distributed tracing out of the box
- Designed for 1M articles/day sustained, with 5-10x burst headroom

## Target Scale
- ~12 writes/sec average, 50-100 writes/sec burst
- Horizontally scalable stateless services; single-writer PostgreSQL with read replicas
- Monthly time-range partitioned article table

## License
[Apache 2.0](./LICENSE)
