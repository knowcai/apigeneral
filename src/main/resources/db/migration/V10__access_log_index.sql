-- 访问日志按时间查询/清理索引
CREATE INDEX IF NOT EXISTS idx_api_access_log_created_at ON api_access_log(created_at);
