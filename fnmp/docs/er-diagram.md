# Database ER Diagram

```mermaid
erDiagram
    article {
        uuid id PK "Part of composite PK"
        timestamp publication_timestamp PK "Part of composite PK"
        varchar headline "NOT NULL, 512 chars"
        text summary
        text body
        varchar source_id FK
        varchar category
        varchar status "PUBLISHED | RETRACTED | DRAFT"
        tsvector search_vector "GIN indexed"
        timestamp created_at
        timestamp updated_at
    }

    source {
        varchar name PK
        varchar reliability_tier "TIER_1 | TIER_2 | TIER_3"
        timestamp created_at
    }

    tag {
        varchar name PK
        timestamp created_at
    }

    article_tag {
        uuid article_id FK
        timestamp publication_timestamp
        varchar tag_name FK
    }

    article_audit {
        bigserial id PK
        uuid article_id
        varchar action "CREATED | RETRACTED | UPDATED"
        jsonb details
        timestamp created_at
    }

    article ||--o{ article_tag : "has"
    article }o--|| source : "sourced by"
    tag ||--o{ article_tag : "tagged in"
    article ||--o{ article_audit : "audited by"
```

## Key Design Decisions

### Partitioning
`article` is range-partitioned by `publication_timestamp` monthly. Partitions are auto-created 4 months ahead.

### Composite Primary Key
Using `(id, publication_timestamp)` enables:
- Efficient time-range scans via partition pruning
- Cursor-based pagination using `(pub_ts, id)` tuples
- Unique identification without a separate sequence

### Full-Text Search
`search_vector` is a generated `tsvector` column combining headline, summary, and body with weighted ranks. The GIN index enables fast `plainto_tsquery` lookups as a fallback when OpenSearch is unavailable.

### Soft Delete
Articles are marked as `RETRACTED` rather than deleted. A partial index (`WHERE status != 'RETRACTED'`) optimizes active queries.
