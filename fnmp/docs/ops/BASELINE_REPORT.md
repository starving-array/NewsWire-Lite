# Baseline Performance Report

> Generated: <!-- date -->
> Environment: <!-- e.g., c6a.4xlarge (16 vCPU, 32 GB) -->
> Data: 1M articles, 500 sources, 5K tags in PostgreSQL / OpenSearch

## Load Test: Write-Heavy (k6)

| Scenario | Target RPS | Actual RPS | p50 | p95 | p99 | Error % |
|---|---|---|---|---|---|---|
| Article creation | 50 | — | — | — | — | — |
| Article creation | 100 | — | — | — | — | — |
| Article creation | 200 | — | — | — | — | — |

## Load Test: Read-Heavy (k6)

| Scenario | Target RPS | Actual RPS | p50 | p95 | p99 | Error % |
|---|---|---|---|---|---|---|
| GET /articles/{id} | 200 | — | — | — | — | — |
| GET /articles (list) | 200 | — | — | — | — | — |
| GET /articles/search | 200 | — | — | — | — | — |

## Load Test: Mixed

| Scenario | Target RPS | Actual RPS | p50 | p95 | p99 | Error % |
|---|---|---|---|---|---|---|
| 80% reads / 20% writes | 150 | — | — | — | — | — |
| 50% reads / 50% writes | 100 | — | — | — | — | — |

## Soak Test (4 hours)

| Metric | Value |
|---|---|
| Peak RPS | — |
| p99 (end) | — |
| Error rate | — |
| Memory leak? | — |

## Infrastructure Metrics

| Resource | Peak Usage | Avg Usage |
|---|---|---|
| article-service CPU | — | — |
| article-service heap | — | — |
| PostgreSQL connections | — | — |
| Redis memory | — | — |
| Kafka throughput | — | — |
| OpenSearch query latency | — | — |

## Key Findings

<!-- Document bottlenecks, tuning adjustments, and limits found -->

## Cache Hit Rates

| Cache | Hit Rate | Miss Rate |
|---|---|---|
| Article single (Redis) | — | — |
| Article list (Redis) | — | — |
| OpenSearch query cache | — | — |

## Recommendations

<!-- Based on baseline results -->
