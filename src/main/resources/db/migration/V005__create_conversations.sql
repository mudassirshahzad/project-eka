-- ============================================================
-- V005: Conversations and Messages
-- Persistent conversational memory store.
-- ============================================================

CREATE TABLE conversations (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES users (id),
    tenant_id  UUID         NOT NULL REFERENCES tenants (id),
    title      VARCHAR(500),
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_conversations_user_id   ON conversations (user_id);
CREATE INDEX idx_conversations_tenant_id ON conversations (tenant_id);
CREATE INDEX idx_conversations_created   ON conversations (created_at DESC);
CREATE INDEX idx_conversations_active    ON conversations (user_id, deleted_at) WHERE deleted_at IS NULL;

CREATE TABLE messages (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID        NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    role            VARCHAR(20) NOT NULL,
    content         TEXT        NOT NULL,
    query_id        UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_messages_role CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM'))
);

CREATE INDEX idx_messages_conversation ON messages (conversation_id, created_at ASC);

COMMENT ON TABLE  conversations IS 'Groups related messages into a session';
COMMENT ON TABLE  messages      IS 'Immutable once written — never UPDATE content';
COMMENT ON COLUMN messages.role IS 'USER | ASSISTANT | SYSTEM';
