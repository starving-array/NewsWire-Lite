# Security Audit Checklist — FNMP

## Authentication & Authorization
- [x] OAuth2/OIDC resource server with JWT validation
- [x] Role-based access control via `@PreAuthorize` (ADMIN, EDITOR, READER)
- [x] Stateless session management (`SessionCreationPolicy.STATELESS`)
- [x] Local profile bypasses auth for development
- [ ] External IdP (Keycloak/Auth0) configured with appropriate token expiry
- [ ] Service accounts with least-privilege for inter-service communication

## API Security
- [x] Input validation via Bean Validation (`jakarta.validation`)
- [x] URI versioning (`/api/v1/...`) for backward-compatible changes
- [x] RFC 7807 `application/problem+json` error responses (no stack traces leaked)
- [x] Parameterized queries via JPA (no SQL injection)
- [x] Request size limits configured in application.yml
- [ ] Rate limiting at gateway/ingress level
- [ ] CORS configuration for known origins only
- [ ] API key validation for partner/ingestion endpoints

## Data Security
- [x] Secrets via environment variables (not hardcoded)
- [ ] Secrets stored in Vault/K8s Secrets at rest
- [x] PostgreSQL connection uses `ssl` in non-local profiles
- [ ] PII/classification review: no regulated PII in scope
- [ ] Data retention policy documented (articles >18mo archived)

## Dependency Security
- [x] OWASP Dependency-Check in CI pipeline (fails on CVSS ≥ 7)
- [x] Trivy container scanning in CI
- [x] Gitleaks + TruffleHog secret scanning in CI
- [ ] Dependencies updated weekly via Dependabot/Renovate
- [ ] Base images pinned to specific digests

## Infrastructure Security
- [x] Non-root user in Docker containers (`fnmp` user)
- [x] Security context with dropped capabilities in K8s manifests
- [x] Health endpoints exposed but limited to `/actuator/health`, `/info`, `/prometheus`
- [ ] Network policies restricting inter-service traffic
- [ ] Pod Security Standards enforced
- [ ] WAF rules in front of ingress

## Logging & Monitoring
- [x] Structured JSON logging (logstash-logback-encoder)
- [x] Trace IDs in every log line for audit trail
- [x] Audit log for all article create/delete operations
- [ ] Alerts configured for security events (auth failures, suspicious patterns)
- [ ] Log retention policy documented

## Operational Security
- [ ] Backup encryption at rest and in transit
- [ ] Disaster recovery plan documented (see DR_RUNBOOK.md)
- [ ] Incident response runbook documented
- [ ] Secrets rotation procedure documented
- [ ] Access reviews conducted quarterly