package com.apigateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gateway.access-log")
public class AccessLogRetentionProperties {

    private boolean cleanupEnabled = true;

    /** 保留天数，超出后物理删除。 */
    private int retentionDays = 90;

    /** 每日清理 cron，默认凌晨 3 点。 */
    private String cleanupCron = "0 0 3 * * *";
}
