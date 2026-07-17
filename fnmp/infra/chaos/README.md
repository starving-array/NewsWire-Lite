# Chaos Engineering Scenarios

## Prerequisites
- kubectl configured for staging cluster
- Docker Compose running locally for local chaos

---

## Automated Chaos Tests

Automated scripts for Kubernetes-based chaos testing are available:

| Script | Failure Scenario | Verifies |
|---|---|---|
| `chaos-opensearch-outage.sh` | OpenSearch down | PG `tsvector` fallback, circuit breaker |
| `chaos-pg-outage.sh` | PostgreSQL primary down | Read replica fallback, readiness probe |
| `chaos-kafka-outage.sh` | Kafka broker down | Ingestion degradation, event queuing |
| `chaos-redis-outage.sh` | Redis down | DB fallback, cache miss handling |

Run locally:
```bash
NAMESPACE=fnmp-staging bash infra/chaos/chaos-opensearch-outage.sh
```

See `graceful-degradation-test.js` for k6-based local chaos validation.

---

## Scenario 2: PostgreSQL Primary Outage

**Objective**: Verify read replicas serve traffic, writes are queued.

**Steps**:
```bash
# 1. Start k6 read-heavy test
k6 run infra/k6/read-heavy-test.js

# 2. In another terminal, simulate PG primary failure
# (In production: kill primary pod; locally: stop postgres)
docker compose -f infra/docker-compose.yml stop postgres

# 3. Verify:
#   - GET /articles/{id} still works (if read replica configured)
#   - POST /articles returns 503
#   - Health check reflects degraded state

# 4. Restart PostgreSQL
docker compose -f infra/docker-compose.yml start postgres

# 5. Verify full recovery
```

**Expected behavior**:
- Read operations continue via read replicas
- Write operations return appropriate error (503)
- Actuator health reflects DB connectivity loss
- Auto-recovery when PG is restored

---

## Scenario 3: Kafka Outage

**Objective**: Verify direct article creation still works, async operations queue.

**Steps**:
```bash
# 1. Start k6 mixed workload
k6 run infra/k6/load-test.js

# 2. Stop Kafka
docker compose -f infra/docker-compose.yml stop kafka

# 3. Verify:
#   - POST /api/v1/articles (article-service) still works (direct DB write)
#   - POST /api/v1/articles (ingestion-service) fails or returns 202 but event not processed
#   - Consumer health check fails

# 4. Restart Kafka
docker compose -f infra/docker-compose.yml start kafka

# 5. Verify consumers catch up and process queued events
```

**Expected behavior**:
- Article-service create endpoint continues working (direct DB write + local cache evict)
- Ingestion-service returns 202 but articles won't appear in PG until Kafka recovers
- Kafka events are buffered and replayed after restart

---

## Scenario 4: Redis Outage

**Objective**: Verify cache miss fallback works without errors.

**Steps**:
```bash
# 1. Start k6 read-heavy test
k6 run infra/k6/read-heavy-test.js

# 2. Stop Redis
docker compose -f infra/docker-compose.yml stop redis

# 3. Verify:
#   - All endpoints still return 200 (reading from DB directly)
#   - Latency may increase (cache misses)

# 4. Restart Redis
docker compose -f infra/docker-compose.yml start redis

# 5. Verify cache warms up naturally
```

**Expected behavior**:
- All operations continue without errors
- Latency increases due to DB fallback
- No data loss (Redis is ephemeral cache)

---

## Scenario 5: Pod Failure (Kubernetes)

**Objective**: Verify Kubernetes auto-healing.

**Steps**:
```bash
# 1. Delete a pod
kubectl delete pod -l app=article-service -n fnmp-staging

# 2. Verify:
#   - New pod starts within 30s
#   - No 5xx errors during transition (load balancer retries)
#   - HPA maintains desired replica count
```

**Expected behavior**:
- Kubernetes ReplicaSet creates replacement pod immediately
- Readiness probe delays traffic until pod is healthy
- Zero downtime during rolling replacement
- HPA maintains target replica count