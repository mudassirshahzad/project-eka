-- ============================================================
-- V003: Documents
-- Tracks ingestion state machine + document metadata.
-- ============================================================

CREATE TABLE documents (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID         NOT NULL REFERENCES tenants (id),
    owner_id         UUID         NOT NULL REFERENCES users (id),

    -- File identity
    filename         VARCHAR(500) NOT NULL,
    format           VARCHAR(20)  NOT NULL,  -- SupportedFormat enum
    status           VARCHAR(20)  NOT NULL,  -- DocumentStatus enum

    -- Rich metadata (filterable)
    title            VARCHAR(500),
    author           VARCHAR(255),
    description      TEXT,
    department       VARCHAR(255),
    classification   VARCHAR(50),
    tags             TEXT[],

    -- Ingestion tracking
    raw_content_path VARCHAR(1000),
    chunk_count      INT          NOT NULL DEFAULT 0,
    ingestion_error  TEXT,

    -- Lifecycle
    deleted_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_documents_format     CHECK (format IN ('PDF','DOCX','PPTX','XLSX','TXT','CSV','HTML','MARKDOWN')),
    CONSTRAINT chk_documents_status     CHECK (status IN ('PENDING','PARSING','CHUNKING','EMBEDDING','INDEXED','FAILED')),
    CONSTRAINT chk_documents_chunk_count CHECK (chunk_count >= 0)
);

CREATE INDEX idx_documents_tenant_status  ON documents (tenant_id, status);
CREATE INDEX idx_documents_tenant_owner   ON documents (tenant_id, owner_id);
CREATE INDEX idx_documents_tenant_dept    ON documents (tenant_id, department);
CREATE INDEX idx_documents_created        ON documents (created_at DESC);
CREATE INDEX idx_documents_deleted_at     ON documents (deleted_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_documents_tags           ON documents USING GIN (tags);

COMMENT ON TABLE  documents                 IS 'One row per uploaded file; vectors live in Weaviate';
COMMENT ON COLUMN documents.raw_content_path IS 'Path to raw stored file — relative to storage root';
COMMENT ON COLUMN documents.chunk_count      IS 'Updated atomically when ingestion reaches INDEXED';
