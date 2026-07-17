# Demo Script

## Prerequisites
- Docker Desktop running
- `docker compose up -d` started in `fnmp/infra/` (PostgreSQL + Redis)
- App running on `http://localhost:8080`
- PowerShell 5.1+

## Usage
```powershell
# Basic CRUD demo
.\demo.ps1

# With burst test (20 concurrent creates)
.\demo.ps1 -RunBurstTest

# Custom URL + 50-request burst
.\demo.ps1 -BaseUrl http://localhost:8080 -RunBurstTest -BurstCount 50
```

## What it demonstrates
1. Health check (`/actuator/health`)
2. Create article (`POST /api/v1/articles`)
3. Get by ID (`GET /api/v1/articles/{id}`)
4. List with pagination (`GET /api/v1/articles?size=5`)
5. Full-text search (`GET /api/v1/articles/search?q=...`)
6. Error handling — RFC 7807 compliant (400 on invalid UUID, 409 on duplicate)
7. Delete (`DELETE /api/v1/articles/{id}`)
8. Burst test — parallel article creates to show Kafka buffering + async ingestion

## k6 Load Tests
For heavier traffic simulation, use the k6 scripts:
```powershell
k6 run infra/k6/load-test.js
k6 run infra/k6/read-heavy-test.js
```
