package com.apigateway.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class ProductionSecurityValidator {

    private final Environment environment;

    @EventListener(ApplicationReadyEvent.class)
    public void validate() {
        requireEnv("GATEWAY_JWT_SECRET", "gateway.auth.jwt-secret");
        requireEnv("GATEWAY_ADMIN_PASSWORD", "gateway.auth.default-admin-password");
        requireEnv("GATEWAY_KEY_PEPPER", "gateway.consumer.key-pepper");
        requireEnv("GATEWAY_SECRET_ENCRYPTION_KEY", "gateway.secrets.encryption-key");
        String bootstrapKey = environment.getProperty("gateway.consumer.bootstrap-key", "");
        if (bootstrapKey != null && !bootstrapKey.isBlank()) {
            throw new IllegalStateException("生产环境禁止设置 gateway.consumer.bootstrap-key");
        }
        log.info("生产环境密钥校验通过");
    }

    private void requireEnv(String envName, String propertyKey) {
        String value = environment.getProperty(propertyKey);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("生产环境必须设置 " + envName);
        }
        if (value.length() < 16) {
            throw new IllegalStateException(envName + " 长度至少 16 字符");
        }
    }
}
