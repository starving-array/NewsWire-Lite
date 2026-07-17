#!/bin/bash
# Chaos scenario: Simulate OpenSearch outage and verify graceful degradation
# Usage: ./chaos-opensearch-outage.sh

set -euo pipefail

NAMESPACE="${NAMESPACE:-fnmp-staging}"
DURATION="${DURATION:-120}"  # seconds to keep OpenSearch down

echo "=== Chaos: OpenSearch Outage ==="
echo "Namespace: $NAMESPACE"
echo "Duration: ${DURATION}s"
echo ""

# Start monitoring in background
echo "Starting k6 monitoring..."
k6 run ../chaos/graceful-degradation-test.js &
K6_PID=$!
sleep 5

if [[ "$KUBERNETES_SERVICE_HOST" != "" ]]; then
    echo "Scaling OpenSearch to 0 replicas..."
    kubectl scale statefulset opensearch -n "$NAMESPACE" --replicas=0
    
    echo "Waiting ${DURATION}s for chaos verification..."
    sleep "$DURATION"
    
    echo "Restoring OpenSearch..."
    kubectl scale statefulset opensearch -n "$NAMESPACE" --replicas=1
    kubectl wait --for=condition=ready pod -l app=opensearch -n "$NAMESPACE" --timeout=120s
else
    echo "Running locally - stopping OpenSearch container..."
    docker compose -f ../docker-compose.yml stop opensearch
    
    echo "Waiting ${DURATION}s for chaos verification..."
    sleep "$DURATION"
    
    echo "Restoring OpenSearch..."
    docker compose -f ../docker-compose.yml start opensearch
fi

echo "Waiting for k6 to finish..."
wait $K6_PID

echo ""
echo "=== Chaos scenario complete ==="
echo "Verify:"
echo "  - k6 error rate should be < 10%"
echo "  - Search service returned empty results (not errors)"
echo "  - Article service continued serving PG fallback search"