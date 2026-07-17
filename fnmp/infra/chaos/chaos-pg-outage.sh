#!/usr/bin/env bash
set -euo pipefail

# Chaos: PostgreSQL Primary Outage
# Tests that article-service gracefully degrades when PostgreSQL goes down

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib/chaos-common.sh" 2>/dev/null || {
  echo "INFO: chaos-common.sh not found, using direct commands"
}

NAMESPACE="${NAMESPACE:-fnmp-staging}"
PG_POD="${PG_POD:-$(kubectl get pod -l app=postgresql -n "$NAMESPACE" -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")}"

echo "=== Chaos: PostgreSQL Primary Outage ==="
echo "Namespace: $NAMESPACE"
echo "Start time: $(date -u +%H:%M:%S)"

# 1. Verify baseline health
echo "--- Verifying baseline ---"
kubectl exec -n "$NAMESPACE" deploy/article-service -- wget -qO- http://localhost:8080/actuator/health 2>/dev/null

# 2. Simulate PG outage (scale to 0 or kill primary)
echo "--- Killing PostgreSQL ---"
if [ -n "$PG_POD" ]; then
  kubectl exec "$PG_POD" -n "$NAMESPACE" -- kill 1 2>/dev/null || true
  kubectl delete pod "$PG_POD" -n "$NAMESPACE" --grace-period=1 2>/dev/null || true
else
  kubectl scale statefulset/postgresql --replicas=0 -n "$NAMESPACE" 2>/dev/null || \
  kubectl scale deployment/postgresql --replicas=0 -n "$NAMESPACE" 2>/dev/null || \
  echo "WARN: Could not scale PostgreSQL"
fi

echo "Waiting for connection pool to drain..."
sleep 15

# 3. Verify graceful degradation
echo "--- Checking graceful degradation ---"
HEALTH_STATUS=$(kubectl exec -n "$NAMESPACE" deploy/article-service -- \
  wget -qO- http://localhost:8080/actuator/health 2>/dev/null || echo "UNHEALTHY")
echo "Health status: $HEALTH_STATUS"

# Readiness should be DOWN but liveness UP
READY_STATUS=$(kubectl exec -n "$NAMESPACE" deploy/article-service -- \
  wget -qO- http://localhost:8080/actuator/health/readiness 2>/dev/null || echo "UNHEALTHY")
echo "Readiness: $READY_STATUS"

# API should return 503 (circuit breaker open) not 5xx
echo "--- Testing API response ---"
API_RESPONSE=$(kubectl exec -n "$NAMESPACE" deploy/article-service -- \
  curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/v1/articles?size=1 2>/dev/null || echo "FAIL")
echo "API HTTP code: $API_RESPONSE"

# 4. Restore PostgreSQL
echo "--- Restoring PostgreSQL ---"
kubectl scale statefulset/postgresql --replicas=1 -n "$NAMESPACE" 2>/dev/null || \
kubectl scale deployment/postgresql --replicas=1 -n "$NAMESPACE" 2>/dev/null || \
echo "WARN: Could not restore PostgreSQL"

echo "=== Chaos test complete ==="
echo "End time: $(date -u +%H:%M:%S)"
echo "Results:"
echo "  Health degraded gracefully: $HEALTH_STATUS"
echo "  Readiness reflects dependency: $READY_STATUS"
echo "  API returns controlled error (503): $API_RESPONSE"
