-- ============================================================
-- V008: Audit Logs
-- Append-only compliance log. BIGSERIAL PK for cheap range
-- scans. Grant only INSERT to the application role.
-- ============================================================

CREATE TABLE audit_logs (
    id            BIGSERIAL    PRIMARY KEY,
    tenant_id     UUID         NOT NULL,
    user_id       UUID,
    action        VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id   VARCHAR(255),
    details       JSONB,
    ip_address    INET,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_tenant_time ON audit_logs (tenant_id, created_at DESC);
CREATE INDEX idx_audit_user_time   ON audit_logs (user_id,   created_at DESC) WHERE user_id IS NOT NULL;
CREATE INDEX idx_audit_action      ON audit_logs (action);
CREATE INDEX idx_audit_resource    ON audit_logs (resource_type, resource_id);
CREATE INDEX idx_audit_details     ON audit_logs USING GIN (details) WHERE details IS NOT NULL;

-- Enforce immutability at database level
CREATE RULE no_update_audit_logs AS ON UPDATE TO audit_logs DO INSTEAD NOTHING;
CREATE RULE no_delete_audit_logs AS ON DELETE TO audit_logs DO INSTEAD NOTHING;

COMMENT ON TABLE  audit_logs         IS 'Immutable compliance log — INSERT only, no UPDATE/DELETE';
COMMENT ON COLUMN audit_logs.details IS 'JSONB blob for action-specific context (before/after state, etc.)';
