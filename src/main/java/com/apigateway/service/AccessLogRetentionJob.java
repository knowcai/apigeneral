package com.apigateway.service;

import com.apigateway.repository.ApiAccessLogRepository;
import com.apigateway.config.AccessLogRetentionProperties;
import com.apigateway.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "gateway.access-log.cleanup-enabled", havingValue = "true", matchIfMissing = true)
public class AccessLogRetentionJob {

    private final ApiAccessLogRepository repository;
    private final AccessLogRetentionProperties properties;

    @Scheduled(cron = "${gateway.access-log.cleanup-cron:0 0 3 * * *}")
    @Transactional
    public void purgeExpiredLogs() {
        if (!properties.isCleanupEnabled()) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(properties.getRetentionDays());
        long deleted = repository.deleteByCreatedAtBefore(cutoff);
        if (deleted > 0) {
            log.info("访问日志清理完成：删除 {} 条（早于 {}）", deleted, cutoff);
        }
    }
}
