ALTER TABLE workspaces ADD COLUMN is_default BOOLEAN NOT NULL DEFAULT FALSE;

WITH ranked AS (
    SELECT id, row_number() OVER (PARTITION BY owner_user_id ORDER BY created_at, id) AS position
    FROM workspaces
)
UPDATE workspaces
SET is_default = TRUE
FROM ranked
WHERE workspaces.id = ranked.id AND ranked.position = 1;

CREATE UNIQUE INDEX uk_workspaces_one_default_per_owner
    ON workspaces (owner_user_id) WHERE is_default;
