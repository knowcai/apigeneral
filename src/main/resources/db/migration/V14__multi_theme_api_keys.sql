-- 一主题允许多把 API Key 并存
DROP INDEX IF EXISTS ux_consumer_theme_id;

-- 待领取改为按 Key（consumer）维度，仅提交人可领
ALTER TABLE theme_api_key_pickup ADD COLUMN IF NOT EXISTS consumer_id BIGINT;
ALTER TABLE theme_api_key_pickup ADD COLUMN IF NOT EXISTS submitter_id BIGINT;

ALTER TABLE theme_api_key_pickup DROP CONSTRAINT IF EXISTS uk_theme_api_key_pickup_theme;
DROP INDEX IF EXISTS uk_theme_api_key_pickup_theme;
DROP INDEX IF EXISTS idx_theme_api_key_pickup_theme;

DELETE FROM theme_api_key_pickup WHERE consumer_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_pickup_consumer ON theme_api_key_pickup (consumer_id);
