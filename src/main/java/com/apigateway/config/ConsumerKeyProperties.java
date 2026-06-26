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
     * 生产环境务必通过环境变量覆盖，且修改后需轮换所有 Key。
     */
    private String keyPepper = "dev-pepper-change-in-production";

    /** 是否允许无主题绑定的 Legacy Consumer Key 鉴权（全新部署请保持 false）。 */
    private boolean legacyEnabled = false;

    /** Legacy 模式计划下线日期（ISO 日期；legacy-enabled=true 时用于监控提示）。 */
    private String legacySunsetDate = "2026-12-31";
}
