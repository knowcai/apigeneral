package com.apigateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gateway.secrets")
public class SecretProperties {

    /**
     * AES-256 密钥材料（建议 32+ 字符，生产环境通过 GATEWAY_SECRET_ENCRYPTION_KEY 注入）。
     */
    private String encryptionKey = "dev-secret-key-change-in-production!!";
}
