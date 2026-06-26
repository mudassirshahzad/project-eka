-- ============================================================
-- V004: Chunks
-- Stores chunk text for BM25 keyword search and metadata
-- for citation resolution. Vectors live in Weaviate.
-- ============================================================

CREATE TABLE chunks (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id       UUID         NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    tenant_id         UUID         NOT NULL REFERENCES tenants (id),

    -- Content
    sequence_number   INT          NOT NULL,
    content           TEXT         NOT NULL,

    -- Positional metadata (aids citation display)
    page_number       INT,
    section_title     VARCHAR(500),
    start_offset      INT,
    end_offset        INT,
    token_count       INT,
    chunking_strategy VARCHAR(50)  NOT NULL,

    -- Weaviate cross-reference — the join key between stores
    vector_id         VARCHAR(255) NOT NULL,

    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_chunks_vector_id      UNIQUE (vector_id),
    CONSTRAINT uq_chunks_doc_sequence   UNIQUE (document_id, sequence_number),
    CONSTRAINT chk_chunks_token_count   CHECK (token_count IS NULL OR token_count > 0)
);

CREATE INDEX idx_chunks_document_id  ON chunks (document_id);
CREATE INDEX idx_chunks_tenant_id    ON chunks (tenant_id);
CREATE INDEX idx_chunks_vector_id    ON chunks (vector_id);

-- Full-text search index for BM25 hybrid retrieval
CREATE INDEX idx_chunks_fts ON chunks USING GIN (to_tsvector('english', content));

COMMENT ON TABLE  chunks           IS 'Chunk metadata + content; vector_id links to Weaviate object';
COMMENT ON COLUMN chunks.vector_id IS 'Weaviate object UUID — reconciliation key between stores';
