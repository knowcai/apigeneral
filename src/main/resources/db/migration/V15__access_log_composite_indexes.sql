-- 监控大盘与访问日志查询：按时间与 API 聚合
CREATE INDEX IF NOT EXISTS idx_api_access_log_created_api ON api_access_log (created_at DESC, api_code);
CREATE INDEX IF NOT EXISTS idx_api_access_log_created_status ON api_access_log (created_at DESC, status);
CREATE INDEX IF NOT EXISTS idx_api_access_log_consumer ON api_access_log (created_at DESC, consumer_id) WHERE consumer_id IS NOT NULL;
