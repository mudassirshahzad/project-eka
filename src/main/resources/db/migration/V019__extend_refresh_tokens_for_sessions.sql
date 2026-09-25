-- ============================================================
-- V019: Extend refresh_tokens for tenant-scoped sessions
-- Phase 7 / WP-2 (ADR RT01, RT05).
--
-- refresh_tokens was created in V009 during the Phase 1/2 foundation work and
-- has never been written to by any application code since -- the same
-- "infrastructure scaffolded ahead of its consumer" pattern ADR HD09 recorded
-- for audit_logs. WP-2 is that consumer arriving, so this migration extends the
-- existing table rather than creating a second one.
--
-- V009 already provides: id, user_id (FK, ON DELETE CASCADE), token_hash
-- (UNIQUE + index), expires_at, revoked_at, created_at, and a partial index on
-- active sessions. Only two columns are missing for WP-2.
-- ============================================================

-- Sessions are tenant-scoped like every other entity in this system. Added
-- nullable first so the statement is safe against a table that already holds
-- rows, then backfilled, then tightened.
ALTER TABLE refresh_tokens
    ADD COLUMN tenant_id UUID REFERENCES tenants (id) ON DELETE CASCADE;

-- user_id is NOT NULL with ON DELETE CASCADE, so every surviving row has a
-- resolvable owner and this backfill cannot leave a NULL behind.
UPDATE refresh_tokens rt
   SET tenant_id = u.tenant_id
  FROM users u
 WHERE u.id = rt.user_id
   AND rt.tenant_id IS NULL;

ALTER TABLE refresh_tokens
    ALTER COLUMN tenant_id SET NOT NULL;

-- V009 recorded created_at but not when the session's current secret was issued.
-- They coincide for a new row; the default keeps any pre-existing row valid.
ALTER TABLE refresh_tokens
    ADD COLUMN issued_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- The scheduled purge deletes strictly by expiry (ADR RT01); V009's indexes are
-- all user- or hash-led and cannot serve it.
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);

COMMENT ON COLUMN refresh_tokens.tenant_id IS 'Tenant that owns this session; mirrors the owning user''s tenant';
COMMENT ON COLUMN refresh_tokens.issued_at IS 'When the current secret for this session was issued (changes on rotation)';
COMMENT ON TABLE  refresh_tokens           IS 'One authenticated session; id is the access token''s sid claim, so revoking a row kills every access token bearing it';
