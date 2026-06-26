-- ============================================================
-- V009: Refresh Tokens
-- Stores hashed refresh tokens for rotation and revocation.
-- Never store the raw token — only its SHA-256 hash.
-- ============================================================

CREATE TABLE refresh_tokens (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_tokens_user_id   ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_hash      ON refresh_tokens (token_hash);
CREATE INDEX idx_refresh_tokens_active    ON refresh_tokens (user_id, revoked_at, expires_at)
    WHERE revoked_at IS NULL;

COMMENT ON TABLE  refresh_tokens            IS 'Hashed refresh tokens — supports rotation and per-device revocation';
COMMENT ON COLUMN refresh_tokens.token_hash IS 'SHA-256 hex digest of the raw token — never the raw value';
