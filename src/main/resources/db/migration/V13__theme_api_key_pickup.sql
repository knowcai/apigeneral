CREATE TABLE theme_api_key_pickup (
    id BIGSERIAL PRIMARY KEY,
    theme_id BIGINT NOT NULL,
    approval_request_id BIGINT,
    action VARCHAR(20) NOT NULL,
    encrypted_key VARCHAR(512) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_theme_api_key_pickup_theme UNIQUE (theme_id)
);

CREATE INDEX idx_theme_api_key_pickup_theme ON theme_api_key_pickup (theme_id);
