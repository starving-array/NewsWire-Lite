# Disaster Recovery Runbook — FNMP

## 1. Failure Scenarios

### 1.1 PostgreSQL Primary Failure
**Symptoms**: `/actuator/health` returns DOWN, write operations fail, connection pool exhausted.

**Impact**: All writes blocked. Reads may still work via read replicas.

**Runbook**:
1. Verify failover: `kubectl exec -it postgres-primary-0 -- patronictl list`
2. If automatic failover didn't trigger:
   - `kubectl exec -it postgres-primary-0 -- patronictl failover --master postgres-primary-0 --candidate postgres-replica-0`
3. Verify new primary: `kubectl exec -it postgres-primary-0 -- psql -c "SELECT pg_is_in_recovery()"`
4. Point services to new primary via ConfigMap update
5. Investigate root cause from PostgreSQL logs: `kubectl logs postgres-primary-0 -c postgres`
6. **RTO**: ≤5 min, **RPO**: ≤5 min (asynchronous replication)

### 1.2 Kafka Broker Failure
**Symptoms**: Consumer lag grows, messages fail to produce, DLQ receives events.

**Impact**: Async processing delayed. Direct POST still works (bypasses Kafka).

**Runbook**:
1. Check cluster state: `kubectl exec kafka-0 -- kafka-broker-api-versions --bootstrap-server localhost:9092`
2. If single-broker failure in multi-broker cluster: traffic rebalances automatically
3. If quorum lost (KRaft):
   - Restart failed broker: `kubectl delete pod kafka-1`
   - Verify controller: `kubectl exec kafka-0 -- kafka-metadata-quorum --bootstrap-server localhost:9092 describe --status`
4. Check consumer lag: `kubectl exec kafka-0 -- kafka-consumer-groups --bootstrap-server localhost:9092 --group article-service-persister --describe`
5. Replay from DLQ if needed: `kubectl exec kafka-0 -- kafka-console-producer --bootstrap-server localhost:9092 --topic article.created < dlq-messages.json`
6. **RTO**: ≤2 min, **RPO**: 0 (Kafka retains messages)

### 1.3 OpenSearch Outage
**Symptoms**: Search queries return empty results (graceful degradation to PG fallback), indexing fails.

**Impact**: Search latency increases, results may be less relevant (PG `tsvector`).

**Runbook**:
1. Check cluster health: `curl http://opensearch:9200/_cluster/health`
2. If red status:
   - Check shard allocation: `curl http://opensearch:9200/_cat/shards`
   - If disk full: increase disk size or run force merge
   - If node down: `kubectl delete pod opensearch-0`
3. Re-index from Kafka: restart search-service consumer to reprocess from last offset
4. **RTO**: ≤5 min (graceful degradation during recovery), **RPO**: <1 min (Kafka replay)

### 1.4 Redis Outage
**Symptoms**: Cache misses increase, DB load spikes.

**Impact**: Higher latency on article reads (DB fallback), no cache invalidation during outage.

**Runbook**:
1. Check connectivity: `redis-cli -h redis ping`
2. Restart Redis: `kubectl delete pod redis-master-0`
3. Cache warms naturally as requests come in
4. **RTO**: ≤1 min, **RPO**: 0 (cache is ephemeral)

### 1.5 Application Pod Failure
**Symptoms**: 503s from a subset of pods, HPA may scale up.

**Impact**: Reduced capacity, auto-healed by Kubernetes.

**Runbook**:
1. Check pod status: `kubectl get pods -n fnmp-staging`
2. Describe failing pod: `kubectl describe pod <pod-name>`
3. Check logs: `kubectl logs <pod-name> --previous`
4. If OOMKilled: increase memory limits in values.yaml
5. If CrashLoopBackOff: check startup logs for config errors
6. **RTO**: ≤30s (Kubernetes auto-restart), **RPO**: 0 (stateless)

### 1.6 Full Zone/AZ Outage
**Symptoms**: Complete loss of a cloud availability zone.

**Impact**: Multi-AZ deployment tolerated. Single-AZ deployment = full outage.

**Runbook**:
1. Verify multi-AZ spread: `kubectl get pods -o wide | grep -v <failed-zone>`
2. If single-AZ: fail over to DR region (see Section 2)
3. Check surviving replicas handle traffic
4. **RTO**: ≤15 min (with DR), **RPO**: ≤5 min

---

## 2. Cross-Region DR

### 2.1 Prerequisites
- WAL archiving to S3/GCS configured
- Offsite backups taken daily
- Region B Kubernetes cluster provisioned (cold standby)

### 2.2 Failover Procedure
1. Promote DR PostgreSQL from WAL archive
2. Point DNS to DR cluster ingress
3. Deploy services from latest Helm chart
4. Update Kafka mirroring direction
5. Verify data consistency
6. Declare incident resolved

### 2.3 Fallback Procedure
1. Once primary region is healthy, configure reverse replication
2. Promote primary region PostgreSQL
3. Re-point DNS
4. Verify catch-up replication before switching

---

## 3. Backup & Restore

### 3.1 PostgreSQL
- **Schedule**: Daily full backup at 02:00 UTC, continuous WAL archiving
- **Retention**: 30 daily, 12 monthly
- **Restore test**: Executed monthly, documented in backup report
- **Command**: `pg_dump -Fc -h postgres -U fnmp fnmp > fnmp_$(date +%F).dump`
- **Restore**: `pg_restore -h new-postgres -U fnmp -d fnmp --clean fnmp_$(date +%F).dump`

### 3.2 Kafka
- Data retained per topic retention policy (7 days default)
- Offsets stored in Kafka internal topics (backed up via Kafka backup tool)
- Critical messages re-playable from source (ingestion API)

### 3.3 OpenSearch
- Snapshot repository configured on S3/GCS
- **Schedule**: Daily snapshot at 03:00 UTC
- **Restore**: Register snapshot repo, restore index, re-route search traffic

---

## 4. Communication

| Severity | Channel | Response Time | Escalation |
|----------|---------|---------------|------------|
| CRITICAL | PagerDuty call | 5 min | Engineering Lead |
| HIGH | Slack #fnmp-alerts | 15 min | On-call Engineer |
| MEDIUM | Slack #fnmp-alerts | 1 hr | Team Lead |
| LOW | Jira ticket | Next business day | — |

---

## 5. Post-Mortem Process
1. Incident timeline documented within 24 hours
2. Root cause identified and verified
3. Action items created with owners and deadlines
4. Runbook updated with lessons learned
5. Retrospective scheduled within 5 business days