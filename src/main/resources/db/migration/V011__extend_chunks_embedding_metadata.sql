-- ============================================================
-- V011: Extend chunks with embedding provenance
--
-- Adds three nullable columns so the system can track WHICH
-- model produced a vector, its dimensionality, and WHEN
-- embedding occurred. Nullable because existing rows predate
-- this migration; values are backfilled on re-indexing.
-- ============================================================

ALTER TABLE chunks
    ADD COLUMN embedding_model     VARCHAR(100),
    ADD COLUMN embedding_dimension INT,
    ADD COLUMN embedded_at         TIMESTAMPTZ;

CREATE INDEX idx_chunks_embedding_model ON chunks (embedding_model)
    WHERE embedding_model IS NOT NULL;

CREATE INDEX idx_chunks_embedded_at ON chunks (embedded_at DESC)
    WHERE embedded_at IS NOT NULL;

COMMENT ON COLUMN chunks.embedding_model     IS 'Ollama model name used to produce the vector, e.g. nomic-embed-text';
COMMENT ON COLUMN chunks.embedding_dimension IS 'Vector dimensionality; mismatch with current model triggers re-indexing';
COMMENT ON COLUMN chunks.embedded_at         IS 'Timestamp the vector was computed and written to Weaviate';
