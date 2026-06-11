CREATE TABLE datasource (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100)  NOT NULL,
    type            VARCHAR(20)   NOT NULL,
    host            VARCHAR(255)  NOT NULL,
    port            INTEGER       NOT NULL,
    database_name   VARCHAR(100)  NOT NULL,
    username        VARCHAR(100),
    password        VARCHAR(500),
    default_params  JSONB         NOT NULL DEFAULT '{}',
    env             VARCHAR(20)   NOT NULL DEFAULT 'dev',
    readonly        BOOLEAN       NOT NULL DEFAULT TRUE,
    status          VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    description     TEXT,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE api_definition (
    id          BIGSERIAL PRIMARY KEY,
    api_code    VARCHAR(100) NOT NULL UNIQUE,
    name        VARCHAR(200) NOT NULL,
    theme       VARCHAR(100),
    description TEXT,
    created_by  VARCHAR(100),
    updated_by  VARCHAR(100),
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE api_version (
    id               BIGSERIAL PRIMARY KEY,
    api_id           BIGINT      NOT NULL REFERENCES api_definition(id) ON DELETE CASCADE,
    version_no       INTEGER     NOT NULL,
    datasource_id    BIGINT      NOT NULL REFERENCES datasource(id),
    sql_template     TEXT        NOT NULL,
    param_schema     JSONB       NOT NULL DEFAULT '{}',
    response_mode    VARCHAR(20) NOT NULL DEFAULT 'PAGE',
    response_config  JSONB       NOT NULL DEFAULT '{}',
    status           VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_by       VARCHAR(100),
    updated_by       VARCHAR(100),
    published_at     TIMESTAMP,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (api_id, version_no)
);

CREATE TABLE consumer (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    api_key       VARCHAR(64)  NOT NULL UNIQUE,
    department    VARCHAR(100),
    ip_whitelist  JSONB        NOT NULL DEFAULT '[]',
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE api_access_log (
    id              BIGSERIAL PRIMARY KEY,
    request_id      VARCHAR(36)  NOT NULL,
    api_code        VARCHAR(100),
    api_version     INTEGER,
    consumer_id     BIGINT,
    consumer_name   VARCHAR(100),
    client_ip       VARCHAR(50),
    request_params  JSONB,
    response_mode   VARCHAR(20),
    response_rows   BIGINT       NOT NULL DEFAULT 0,
    response_bytes  BIGINT       NOT NULL DEFAULT 0,
    duration_ms     BIGINT,
    status          VARCHAR(20),
    error_message   TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_access_log_api_time ON api_access_log (api_code, created_at DESC);
CREATE INDEX idx_access_log_ip_time ON api_access_log (client_ip, created_at DESC);
