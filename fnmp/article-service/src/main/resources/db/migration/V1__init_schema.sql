-- V1__init_schema.sql
-- Initial schema: partitioned article table, supporting tables, indexes

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- Source table
CREATE TABLE IF NOT EXISTS source (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    reliability_tier VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Tag table
CREATE TABLE IF NOT EXISTS tag (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE
);

-- Partitioned article table
CREATE TABLE IF NOT EXISTS article (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    headline VARCHAR(512) NOT NULL,
    summary TEXT,
    body TEXT,
    source_id UUID NOT NULL REFERENCES source(id),
    publication_timestamp TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
    category VARCHAR(50),
    search_vector TSVECTOR
        GENERATED ALWAYS AS (
            setweight(to_tsvector('english', coalesce(headline, '')), 'A') ||
            setweight(to_tsvector('english', coalesce(summary, '')), 'B')
        ) STORED,
    PRIMARY KEY (id, publication_timestamp)
) PARTITION BY RANGE (publication_timestamp);

-- Article-tag join table
CREATE TABLE IF NOT EXISTS article_tag (
    article_id UUID NOT NULL,
    article_pub_timestamp TIMESTAMPTZ NOT NULL,
    tag_id UUID NOT NULL REFERENCES tag(id),
    PRIMARY KEY (article_id, article_pub_timestamp, tag_id)
);

-- Article audit log
CREATE TABLE IF NOT EXISTS article_audit (
    id BIGSERIAL PRIMARY KEY,
    article_id UUID NOT NULL,
    article_pub_timestamp TIMESTAMPTZ,
    action VARCHAR(20) NOT NULL,
    diff JSONB,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Indexes on partitioned table (created per partition automatically)
CREATE INDEX IF NOT EXISTS idx_article_pubts ON article (publication_timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_article_source ON article (source_id);
CREATE INDEX IF NOT EXISTS idx_article_status ON article (status) WHERE status <> 'PUBLISHED';
CREATE INDEX IF NOT EXISTS idx_article_search_vector ON article USING GIN (search_vector);
CREATE INDEX IF NOT EXISTS idx_article_headline_trgm ON article USING GIN (headline gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_article_category ON article (category);

CREATE INDEX IF NOT EXISTS idx_article_tag_tag_id ON article_tag (tag_id);
CREATE INDEX IF NOT EXISTS idx_article_audit_article_id ON article_audit (article_id);
CREATE INDEX IF NOT EXISTS idx_source_reliability ON source (reliability_tier);

-- Create initial partitions for current and next 3 months
DO $$
DECLARE
    start_date DATE;
    end_date DATE;
    partition_name TEXT;
BEGIN
    start_date := date_trunc('month', now())::DATE;
    FOR i IN 0..3 LOOP
        start_date := start_date + (i * INTERVAL '1 month');
        end_date := start_date + INTERVAL '1 month';
        partition_name := 'article_y' || to_char(start_date, 'YYYY') || 'm' || to_char(start_date, 'MM');
        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS %I PARTITION OF article FOR VALUES FROM (%L) TO (%L)',
            partition_name, start_date, end_date
        );
    END LOOP;
END $$;