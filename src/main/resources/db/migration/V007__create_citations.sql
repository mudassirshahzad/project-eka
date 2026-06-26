-- ============================================================
-- V007: Citations
-- Links a generated message back to the source chunks
-- that provided the grounding evidence.
-- ============================================================

CREATE TABLE citations (
    id              UUID   PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id      UUID   NOT NULL REFERENCES messages (id) ON DELETE CASCADE,
    chunk_id        UUID   NOT NULL REFERENCES chunks (id),
    relevance_score FLOAT  NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_citations_score CHECK (relevance_score BETWEEN 0.0 AND 1.0)
);

CREATE INDEX idx_citations_message_id ON citations (message_id);
CREATE INDEX idx_citations_chunk_id   ON citations (chunk_id);

COMMENT ON TABLE citations IS 'Traceability from assistant response back to source document chunks';
