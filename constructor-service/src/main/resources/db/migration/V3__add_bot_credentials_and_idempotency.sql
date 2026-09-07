CREATE TABLE bot_credentials (
    bot_id UUID PRIMARY KEY REFERENCES bots(id) ON DELETE CASCADE,
    encrypted_token TEXT NOT NULL,
    encrypted_webhook_secret TEXT NOT NULL,
    token_key_id VARCHAR(64) NOT NULL,
    webhook_key_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0)
);

CREATE TABLE bot_idempotency_records (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    bot_id UUID NOT NULL REFERENCES bots(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_bot_idempotency_workspace_key UNIQUE (workspace_id, idempotency_key)
);
CREATE INDEX idx_bot_idempotency_bot ON bot_idempotency_records(bot_id);
