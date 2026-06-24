CREATE TABLE IF NOT EXISTS sys_user (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL UNIQUE,
    password_hash   VARCHAR(200) NOT NULL,
    display_name    VARCHAR(100),
    role            VARCHAR(30)  NOT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS display_name VARCHAR(100);
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS role VARCHAR(30);
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS audit_log (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT,
    username        VARCHAR(50),
    action          VARCHAR(30)  NOT NULL,
    resource_type   VARCHAR(50)  NOT NULL,
    resource_id     VARCHAR(100),
    resource_name   VARCHAR(200),
    detail          TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_log_time ON audit_log (created_at DESC);
