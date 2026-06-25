package com.apigateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gateway.consumer")
public class ConsumerKeyProperties {

    /**
     * API Key 哈希用的服务端密钥（pepper），相当于全局盐。
     * 生产环境务必通过环境变量覆盖，且修改后需轮换所有 Key。
     */
    private String keyPepper = "dev-pepper-change-in-production";
}
