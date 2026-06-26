-- ============================================================
-- V012: Chat Sessions
--
-- A ChatSession is one time-bounded LLM interaction window
-- within a Conversation. One conversation can have many
-- sessions (e.g. a user returns the next day). Sessions carry:
--   - the LLM model identity for the window
--   - cumulative token counts for cost attribution
--   - aggregate latency for SLA monitoring
-- ============================================================

CREATE TABLE chat_sessions (
    id                      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id         UUID         NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    user_id                 UUID         NOT NULL REFERENCES users (id),
    tenant_id               UUID         NOT NULL REFERENCES tenants (id),

    model_id                VARCHAR(100) NOT NULL,
    status                  VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',

    -- Cumulative LLM metrics for this session
    total_prompt_tokens     INT          NOT NULL DEFAULT 0,
    total_completion_tokens INT          NOT NULL DEFAULT 0,
    total_latency_ms        BIGINT       NOT NULL DEFAULT 0,
    message_count           INT          NOT NULL DEFAULT 0,

    -- Lifecycle bounds
    started_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    ended_at                TIMESTAMPTZ,

    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_chat_sessions_status  CHECK (status IN ('ACTIVE','COMPLETED','TIMED_OUT')),
    CONSTRAINT chk_chat_sessions_tokens  CHECK (total_prompt_tokens >= 0
                                            AND total_completion_tokens >= 0),
    CONSTRAINT chk_chat_sessions_latency CHECK (total_latency_ms >= 0),
    CONSTRAINT chk_chat_sessions_count   CHECK (message_count >= 0),
    CONSTRAINT chk_chat_sessions_ended   CHECK (ended_at IS NULL OR ended_at >= started_at)
);

-- Primary access patterns
CREATE INDEX idx_chat_sessions_conversation ON chat_sessions (conversation_id, started_at DESC);
CREATE INDEX idx_chat_sessions_user_tenant  ON chat_sessions (tenant_id, user_id);
CREATE INDEX idx_chat_sessions_model        ON chat_sessions (model_id);

-- Partial index: fast active-session lookup; at most one ACTIVE per conversation
CREATE UNIQUE INDEX uq_chat_sessions_one_active
    ON chat_sessions (conversation_id)
    WHERE status = 'ACTIVE';

COMMENT ON TABLE  chat_sessions                        IS 'One row per LLM interaction window within a conversation';
COMMENT ON COLUMN chat_sessions.status                 IS 'ACTIVE | COMPLETED | TIMED_OUT';
COMMENT ON COLUMN chat_sessions.total_prompt_tokens    IS 'Cumulative prompt tokens across all turns in this session';
COMMENT ON COLUMN chat_sessions.total_completion_tokens IS 'Cumulative completion tokens across all turns in this session';
COMMENT ON COLUMN chat_sessions.model_id               IS 'LLM model active for this session, e.g. qwen3';
