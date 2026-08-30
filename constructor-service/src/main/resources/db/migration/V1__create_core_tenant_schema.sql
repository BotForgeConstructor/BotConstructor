CREATE TABLE platform_users (
    id UUID PRIMARY KEY,
    telegram_user_id BIGINT NOT NULL,
    telegram_username VARCHAR(32),
    first_name VARCHAR(128),
    last_name VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_platform_users_telegram_user_id UNIQUE (telegram_user_id),
    CONSTRAINT ck_platform_users_telegram_user_id_positive CHECK (telegram_user_id > 0),
    CONSTRAINT ck_platform_users_version_non_negative CHECK (version >= 0)
);

CREATE TABLE workspaces (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    owner_user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_workspaces_owner_user
        FOREIGN KEY (owner_user_id) REFERENCES platform_users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_workspaces_name CHECK (char_length(btrim(name)) BETWEEN 1 AND 120),
    CONSTRAINT ck_workspaces_version_non_negative CHECK (version >= 0)
);

CREATE INDEX idx_workspaces_owner_user_id ON workspaces (owner_user_id);

CREATE TABLE memberships (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_memberships_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces (id) ON DELETE CASCADE,
    CONSTRAINT fk_memberships_user
        FOREIGN KEY (user_id) REFERENCES platform_users (id) ON DELETE RESTRICT,
    CONSTRAINT uk_memberships_workspace_user UNIQUE (workspace_id, user_id),
    CONSTRAINT ck_memberships_role CHECK (role IN ('OWNER', 'MEMBER'))
);

CREATE INDEX idx_memberships_user_id ON memberships (user_id);

CREATE TABLE bots (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    telegram_bot_id BIGINT,
    telegram_username VARCHAR(32),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_bots_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces (id) ON DELETE RESTRICT,
    CONSTRAINT uk_bots_telegram_bot_id UNIQUE (telegram_bot_id),
    CONSTRAINT ck_bots_display_name CHECK (char_length(btrim(display_name)) BETWEEN 1 AND 120),
    CONSTRAINT ck_bots_telegram_bot_id_positive CHECK (telegram_bot_id IS NULL OR telegram_bot_id > 0),
    CONSTRAINT ck_bots_version_non_negative CHECK (version >= 0)
);

CREATE INDEX idx_bots_workspace_id ON bots (workspace_id);

-- Compatibility-only table for the management-bot prototype. New features use platform_users.
CREATE TABLE user_data (
    chain_id BIGINT PRIMARY KEY,
    count_of_bots INTEGER,
    plan SMALLINT,
    CONSTRAINT ck_user_data_count_non_negative CHECK (count_of_bots IS NULL OR count_of_bots >= 0),
    CONSTRAINT ck_user_data_plan CHECK (plan IS NULL OR plan BETWEEN 0 AND 3)
);
