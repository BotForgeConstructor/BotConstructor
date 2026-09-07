ALTER TABLE bot_credentials
    ADD COLUMN operation_id UUID,
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE bot_credentials
    ADD CONSTRAINT ck_bot_credentials_status CHECK (status = 'ACTIVE');

CREATE TABLE bot_credential_operations (
    id UUID PRIMARY KEY,
    bot_id UUID NOT NULL REFERENCES bots(id) ON DELETE CASCADE,
    operation_type VARCHAR(16) NOT NULL,
    state VARCHAR(32) NOT NULL,
    encrypted_token TEXT NOT NULL,
    encrypted_webhook_secret TEXT NOT NULL,
    token_key_id VARCHAR(64) NOT NULL,
    webhook_key_id VARCHAR(64) NOT NULL,
    telegram_bot_id BIGINT,
    telegram_username VARCHAR(64),
    failure_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT ck_bot_credential_operation_type
        CHECK (operation_type IN ('CONNECT', 'REPLACE', 'RECONNECT')),
    CONSTRAINT ck_bot_credential_operation_state
        CHECK (state IN ('PENDING_VERIFICATION', 'WEBHOOK_PENDING', 'FAILED', 'COMPLETED'))
);

CREATE INDEX idx_bot_credential_operations_bot_created
    ON bot_credential_operations(bot_id, created_at DESC);

CREATE UNIQUE INDEX uk_bot_credential_operation_in_progress
    ON bot_credential_operations(bot_id)
    WHERE state IN ('PENDING_VERIFICATION', 'WEBHOOK_PENDING');

ALTER TABLE bot_credentials
    ADD CONSTRAINT fk_bot_credentials_operation
        FOREIGN KEY (operation_id) REFERENCES bot_credential_operations(id) ON DELETE SET NULL;

CREATE TABLE bot_credential_history (
    id UUID PRIMARY KEY,
    bot_id UUID NOT NULL REFERENCES bots(id) ON DELETE CASCADE,
    operation_id UUID NOT NULL REFERENCES bot_credential_operations(id) ON DELETE RESTRICT,
    encrypted_token TEXT NOT NULL,
    encrypted_webhook_secret TEXT NOT NULL,
    token_key_id VARCHAR(64) NOT NULL,
    webhook_key_id VARCHAR(64) NOT NULL,
    state VARCHAR(16) NOT NULL,
    replaced_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_bot_credential_history_state CHECK (state = 'REPLACED'),
    CONSTRAINT uk_bot_credential_history_operation UNIQUE (operation_id)
);

CREATE INDEX idx_bot_credential_history_bot
    ON bot_credential_history(bot_id, replaced_at DESC);
