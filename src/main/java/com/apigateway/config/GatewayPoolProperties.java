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
}
