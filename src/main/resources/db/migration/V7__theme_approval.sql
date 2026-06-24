CREATE TABLE IF NOT EXISTS theme (
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(50)  NOT NULL UNIQUE,
    name         VARCHAR(100) NOT NULL,
    description  TEXT,
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS theme_membership (
    theme_id BIGINT      NOT NULL REFERENCES theme(id) ON DELETE CASCADE,
    user_id  BIGINT      NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    role     VARCHAR(20) NOT NULL,
    PRIMARY KEY (theme_id, user_id)
);

CREATE TABLE IF NOT EXISTS theme_approval_step (
    id         BIGSERIAL PRIMARY KEY,
    theme_id   BIGINT      NOT NULL REFERENCES theme(id) ON DELETE CASCADE,
    step_order INT         NOT NULL,
    mode       VARCHAR(20) NOT NULL,
    CONSTRAINT uq_theme_step UNIQUE (theme_id, step_order)
);

CREATE TABLE IF NOT EXISTS theme_approval_step_user (
    step_id    BIGINT NOT NULL REFERENCES theme_approval_step(id) ON DELETE CASCADE,
    user_id    BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    sort_order INT    NOT NULL DEFAULT 0,
    PRIMARY KEY (step_id, user_id)
);

CREATE TABLE IF NOT EXISTS approval_request (
    id                 BIGSERIAL PRIMARY KEY,
    theme_id           BIGINT       NOT NULL REFERENCES theme(id),
    resource_type      VARCHAR(30)  NOT NULL,
    resource_id        BIGINT,
    action             VARCHAR(20)  NOT NULL,
    title              VARCHAR(200),
    payload            TEXT         NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    submitter_id       BIGINT       NOT NULL REFERENCES sys_user(id),
    current_step_order INT          NOT NULL DEFAULT 1,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at        TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_approval_request_status ON approval_request (status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_approval_request_theme ON approval_request (theme_id, status);

CREATE TABLE IF NOT EXISTS approval_task (
    id           BIGSERIAL PRIMARY KEY,
    request_id   BIGINT      NOT NULL REFERENCES approval_request(id) ON DELETE CASCADE,
    step_order   INT         NOT NULL,
    assignee_id  BIGINT      NOT NULL REFERENCES sys_user(id),
    sort_order   INT         NOT NULL DEFAULT 0,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    comment      TEXT,
    acted_at     TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_approval_task_assignee ON approval_task (assignee_id, status);

ALTER TABLE datasource ADD COLUMN IF NOT EXISTS theme_id BIGINT REFERENCES theme(id);
ALTER TABLE api_definition ADD COLUMN IF NOT EXISTS theme_id BIGINT REFERENCES theme(id);

INSERT INTO theme (code, name, description)
SELECT 'default', '默认主题', '迁移自既有数据'
WHERE NOT EXISTS (SELECT 1 FROM theme WHERE code = 'default');

UPDATE api_definition SET theme_id = (SELECT id FROM theme WHERE code = 'default')
WHERE theme_id IS NULL;

UPDATE datasource SET theme_id = (SELECT id FROM theme WHERE code = 'default')
WHERE theme_id IS NULL;

UPDATE api_definition d SET theme = t.code
FROM theme t WHERE d.theme_id = t.id AND (d.theme IS NULL OR d.theme = '');
