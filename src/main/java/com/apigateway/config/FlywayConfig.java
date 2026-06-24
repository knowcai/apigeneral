package com.apigateway.config;

import org.flywaydb.core.api.FlywayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayConfig.class);

    /**
     * 仅执行增量迁移，不删除或重置元数据库中的业务数据。
     */
    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            try {
                flyway.repair();
                flyway.migrate();
            } catch (FlywayException e) {
                log.warn("Flyway 迁移未完全成功，已跳过（不会清除已有数据）: {}", e.getMessage());
            }
        };
    }
}
