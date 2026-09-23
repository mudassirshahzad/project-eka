-- P06.2 Authorization Filter: every document ingested before this milestone has no
-- classification set. Transition default is INTERNAL (backward compatibility) — new uploads
-- are now required to specify a classification (enforced in application code, not here).
--
-- Idempotent by construction: the WHERE clause only ever matches rows still NULL, so re-running
-- this statement (Flyway itself will not — versioned migrations apply exactly once per
-- environment — but this is safe if it's ever re-run by hand) converges to zero affected rows
-- after the first run.
UPDATE documents
SET classification = 'INTERNAL'
WHERE classification IS NULL;
