-- ============================================================
-- V016: Make chunks.vector_id nullable
--
-- P03.2 saves chunks with embedding provenance BEFORE Weaviate
-- indexing (P03.3). The UNIQUE constraint is retained — PostgreSQL
-- treats each NULL as distinct, so multiple NULL vector_ids are
-- allowed while the uniqueness guarantee on real UUIDs is preserved.
-- ============================================================

ALTER TABLE chunks ALTER COLUMN vector_id DROP NOT NULL;

COMMENT ON COLUMN chunks.vector_id IS 'Weaviate object UUID — NULL until P03.3 indexes the chunk; unique among non-NULL values';
