package com.apigateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gateway.pool")
public class GatewayPoolProperties {

    /** 整网关向下游发起的最大并发连接数（所有数据源合计）。 */
    private int globalMaxConnections = 200;

    /** 全局连接预算耗尽时的等待超时（毫秒），超时返回 503。 */
    private long acquireTimeoutMs = 5000;

    /**
     * 网关实例数（水平扩展时配置，用于将 global-max-connections 均分到单实例）。
     */
    private int replicaCount = 1;
}
