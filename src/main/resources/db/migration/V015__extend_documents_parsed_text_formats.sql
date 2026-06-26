-- ============================================================
-- V015: Extend documents — parsed_text_path + expanded formats
-- ============================================================

ALTER TABLE documents
    ADD COLUMN parsed_text_path VARCHAR(1000);

ALTER TABLE documents
    DROP CONSTRAINT chk_documents_format;

ALTER TABLE documents
    ADD CONSTRAINT chk_documents_format
        CHECK (format IN ('PDF','DOCX','DOC','PPTX','XLSX','TXT','CSV','HTML','MARKDOWN','JSON','XML'));

COMMENT ON COLUMN documents.parsed_text_path IS 'Path to Tika-extracted plain-text file — relative to storage root';
