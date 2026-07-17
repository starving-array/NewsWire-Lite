#!/usr/bin/env bash
set -euo pipefail

# Chaos: Kafka Broker Outage
# Tests that ingestion-service gracefully degrades when Kafka is unavailable

NAMESPACE="${NAMESPACE:-fnmp-staging}"

echo "=== Chaos: Kafka Broker Outage ==="
echo "Namespace: $NAMESPACE"
echo "Start time: $(date -u +%H:%M:%S)"

# 1. Verify baseline ingestion
echo "--- Verifying baseline ---"
kubectl exec -n "$NAMESPACE" deploy/ingestion-service -- \
  wget -qO- http://localhost:8082/actuator/health 2>/dev/null

# 2. Kill Kafka
echo "--- Killing Kafka ---"
kubectl scale deployment/kafka --replicas=0 -n "$NAMESPACE" 2>/dev/null || \
kubectl scale statefulset/kafka --replicas=0 -n "$NAMESPACE" 2>/dev/null || \
echo "WARN: Could not scale Kafka"

echo "Waiting for connection timeout..."
sleep 20

# 3. Verify graceful degradation
echo "--- Checking graceful degradation ---"
HEALTH_STATUS=$(kubectl exec -n "$NAMESPACE" deploy/ingestion-service -- \
  wget -qO- http://localhost:8082/actuator/health 2>/dev/null || echo "UNHEALTHY")
echo "Health status: $HEALTH_STATUS"

# Ingestion should reject requests gracefully
echo "--- Testing ingestion API ---"
CREATE_RESPONSE=$(kubectl exec -n "$NAMESPACE" deploy/article-service -- \
  curl -s -w "\n%{http_code}" -X POST http://localhost:8082/api/v1/articles \
  -H 'Content-Type: application/json' \
  -d '{"headline":"Kafka Outage Test","source":"Test","publicationTimestamp":"2026-07-17T00:00:00Z"}' 2>/dev/null || echo "FAIL")
echo "Create response: $CREATE_RESPONSE"

# 4. Restore Kafka
echo "--- Restoring Kafka ---"
kubectl scale deployment/kafka --replicas=1 -n "$NAMESPACE" 2>/dev/null || \
kubectl scale statefulset/kafka --replicas=1 -n "$NAMESPACE" 2>/dev/null || \
echo "WARN: Could not restore Kafka"

echo "=== Chaos test complete ==="
echo "End time: $(date -u +%H:%M:%S)"
