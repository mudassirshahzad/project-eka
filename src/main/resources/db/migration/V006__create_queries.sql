-- ============================================================
-- V006: Queries
-- Traceability record for every retrieval execution.
-- Powers analytics, latency monitoring, and audit.
-- ============================================================

CREATE TABLE queries (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL REFERENCES users (id),
    tenant_id       UUID        NOT NULL REFERENCES tenants (id),
    conversation_id UUID        REFERENCES conversations (id) ON DELETE SET NULL,

    original_text   TEXT        NOT NULL,
    rewritten_text  TEXT,
    filter_json     JSONB,
    retrieved_count INT,
    latency_ms      BIGINT,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_queries_latency CHECK (latency_ms IS NULL OR latency_ms >= 0)
);

CREATE INDEX idx_queries_tenant_user ON queries (tenant_id, user_id);
CREATE INDEX idx_queries_conversation ON queries (conversation_id);
CREATE INDEX idx_queries_created      ON queries (created_at DESC);
CREATE INDEX idx_queries_filter       ON queries USING GIN (filter_json) WHERE filter_json IS NOT NULL;

COMMENT ON TABLE  queries             IS 'One row per query execution — never modified after insert';
COMMENT ON COLUMN queries.filter_json IS 'Serialised MetadataFilter applied during retrieval';
