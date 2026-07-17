#!/usr/bin/env bash
set -euo pipefail

# Chaos: Redis Outage
# Tests that article-service gracefully falls through to DB when Redis is unavailable

NAMESPACE="${NAMESPACE:-fnmp-staging}"

echo "=== Chaos: Redis Outage ==="
echo "Namespace: $NAMESPACE"
echo "Start time: $(date -u +%H:%M:%S)"

# 1. Verify cache hit works
echo "--- Verifying cache baseline ---"
kubectl exec -n "$NAMESPACE" deploy/article-service -- \
  wget -qO- http://localhost:8080/actuator/health 2>/dev/null

# 2. Kill Redis
echo "--- Killing Redis ---"
kubectl scale deployment/redis --replicas=0 -n "$NAMESPACE" 2>/dev/null || \
kubectl scale statefulset/redis --replicas=0 -n "$NAMESPACE" 2>/dev/null || \
echo "WARN: Could not scale Redis"

echo "Waiting for connection timeout..."
sleep 10

# 3. Verify graceful degradation (app should still work, hitting DB directly)
echo "--- Checking graceful degradation ---"
HEALTH_STATUS=$(kubectl exec -n "$NAMESPACE" deploy/article-service -- \
  wget -qO- http://localhost:8080/actuator/health 2>/dev/null || echo "UNHEALTHY")
echo "Health status: $HEALTH_STATUS"

# API should still return 200 (DB fallback, no circuit breaker for cache)
echo "--- Testing API response ---"
API_RESPONSE=$(kubectl exec -n "$NAMESPACE" deploy/article-service -- \
  curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/v1/articles?size=1 2>/dev/null || echo "FAIL")
echo "API HTTP code: $API_RESPONSE"

# 4. Restore Redis
echo "--- Restoring Redis ---"
kubectl scale deployment/redis --replicas=1 -n "$NAMESPACE" 2>/dev/null || \
kubectl scale statefulset/redis --replicas=1 -n "$NAMESPACE" 2>/dev/null || \
echo "WARN: Could not restore Redis"

echo "=== Chaos test complete ==="
echo "End time: $(date -u +%H:%M:%S)"
