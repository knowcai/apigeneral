ALTER TABLE api_access_log ADD COLUMN IF NOT EXISTS auth_mode VARCHAR(20);

CREATE INDEX IF NOT EXISTS idx_access_log_auth_mode_created ON api_access_log (auth_mode, created_at);
