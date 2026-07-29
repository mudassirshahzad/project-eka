-- ============================================================
-- V017: Fix roles/created_at schema drift (P04.13.4 reconciliation)
-- ============================================================
-- RoleEntity extends BaseUuidEntity, which requires a created_at column.
-- V002 never defined one on `roles`, so Hibernate schema validation
-- (spring.jpa.hibernate.ddl-auto: validate) has always failed against a
-- real database for this table. Undetected until now because no test in
-- this codebase had ever booted the full JPA context (see P04.13 audit).

ALTER TABLE roles
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
