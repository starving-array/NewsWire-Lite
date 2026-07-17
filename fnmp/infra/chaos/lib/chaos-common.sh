#!/usr/bin/env bash
# Chaos Engineering - shared utilities

log() {
  local level="$1"
  shift
  echo "[$(date -u +%H:%M:%S)] [$level] $*"
}

info()  { log "INFO" "$@"; }
warn()  { log "WARN" "$@"; }
error() { log "ERROR" "$@"; }
ok()    { log "OK"   "$@"; }

assert_status() {
  local actual="$1"
  local expected="$2"
  local label="$3"
  if [ "$actual" = "$expected" ]; then
    ok "$label: $actual (expected $expected)"
  else
    error "$label: $actual (expected $expected)"
  fi
}

measure_latency() {
  local url="$1"
  local result
  result=$(curl -s -o /dev/null -w "%{http_code},%{time_total}" "$url" 2>/dev/null || echo "FAIL,0")
  echo "$result"
}
