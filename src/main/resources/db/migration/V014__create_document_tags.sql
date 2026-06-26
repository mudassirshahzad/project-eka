-- ============================================================
-- V014: Document Tags (normalised taxonomy)
--
-- Complements the documents.tags text[] column with a
-- structured tag table. Benefits over the array column:
--   - Enables cross-document tag search without GIN scans
--   - Supports optional category namespacing (department,
--     classification, project, topic, etc.)
--   - Provides per-tag audit trail (who added it, when)
--   - Case-insensitive uniqueness enforced at DB level
--
-- The documents.tags array is preserved for Weaviate metadata
-- sync; it should be kept in sync by the application layer.
-- ============================================================

CREATE TABLE document_tags (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID         NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    tenant_id   UUID         NOT NULL REFERENCES tenants (id),
    tag         VARCHAR(100) NOT NULL,
    category    VARCHAR(50),
    created_by  UUID         NOT NULL REFERENCES users (id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_document_tags_tag_length  CHECK (char_length(tag) >= 1)
);

-- Case-insensitive uniqueness: no duplicate tag per document regardless of case
CREATE UNIQUE INDEX uq_document_tags_doc_tag
    ON document_tags (document_id, lower(tag));

-- Per-document tag listing
CREATE INDEX idx_document_tags_document
    ON document_tags (document_id);

-- Cross-document tag search within a tenant (faceted browsing)
CREATE INDEX idx_document_tags_tenant_tag
    ON document_tags (tenant_id, lower(tag));

-- Category-based browsing within a tenant
CREATE INDEX idx_document_tags_tenant_category
    ON document_tags (tenant_id, category)
    WHERE category IS NOT NULL;

COMMENT ON TABLE  document_tags          IS 'Normalised tag taxonomy; complements documents.tags text[] for structured governance';
COMMENT ON COLUMN document_tags.tag      IS 'Stored and indexed in lowercase; application normalises before insert';
COMMENT ON COLUMN document_tags.category IS 'Optional namespace, e.g. department | classification | project | topic';
