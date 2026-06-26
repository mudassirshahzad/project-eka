-- ============================================================
-- V013: Extend messages with session linkage and LLM metadata
--
-- Adds per-message operational telemetry. All columns are
-- nullable because:
--   1. Existing rows predate sessions and token tracking.
--   2. USER/SYSTEM role messages never carry LLM metadata.
-- ============================================================

ALTER TABLE messages
    ADD COLUMN session_id        UUID        REFERENCES chat_sessions (id) ON DELETE SET NULL,
    ADD COLUMN prompt_tokens     INT,
    ADD COLUMN completion_tokens INT,
    ADD COLUMN model_id          VARCHAR(100),
    ADD COLUMN latency_ms        BIGINT,
    ADD COLUMN finish_reason     VARCHAR(50);

-- Session membership index: retrieve all messages within a session in order
CREATE INDEX idx_messages_session
    ON messages (session_id, created_at ASC)
    WHERE session_id IS NOT NULL;

COMMENT ON COLUMN messages.session_id        IS 'FK to chat_sessions; links message to its interaction window';
COMMENT ON COLUMN messages.prompt_tokens     IS 'Input tokens sent to the LLM; null for USER/SYSTEM messages';
COMMENT ON COLUMN messages.completion_tokens IS 'Output tokens from the LLM; null for USER/SYSTEM messages';
COMMENT ON COLUMN messages.model_id          IS 'LLM model that generated this response';
COMMENT ON COLUMN messages.latency_ms        IS 'Wall-clock time for the LLM call in milliseconds';
COMMENT ON COLUMN messages.finish_reason     IS 'Why the LLM stopped: STOP | LENGTH | TOOL_CALLS | CONTENT_FILTER';
