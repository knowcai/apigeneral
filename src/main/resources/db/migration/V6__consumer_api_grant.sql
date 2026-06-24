CREATE TABLE IF NOT EXISTS consumer_api_grant (
    consumer_id BIGINT NOT NULL REFERENCES consumer(id) ON DELETE CASCADE,
    api_id      BIGINT NOT NULL REFERENCES api_definition(id) ON DELETE CASCADE,
    PRIMARY KEY (consumer_id, api_id)
);

ALTER TABLE consumer ADD COLUMN IF NOT EXISTS key_prefix VARCHAR(20);
