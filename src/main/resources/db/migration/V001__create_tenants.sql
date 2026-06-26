-- ============================================================
-- V001: Tenants
-- Foundation table — every other table references this.
-- ============================================================

CREATE TABLE tenants (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(255) NOT NULL,
    slug       VARCHAR(100) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_tenants_slug UNIQUE (slug)
);

CREATE INDEX idx_tenants_slug   ON tenants (slug);
CREATE INDEX idx_tenants_active ON tenants (active) WHERE active = TRUE;

COMMENT ON TABLE  tenants            IS 'Top-level tenant isolation boundary';
COMMENT ON COLUMN tenants.slug       IS 'URL-safe identifier used in routing and logging';
