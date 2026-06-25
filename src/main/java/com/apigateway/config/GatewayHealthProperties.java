package com.apigateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gateway.health")
public class GatewayHealthProperties {

    /** 是否在健康检查中探测已配置的下游数据源（Doris/CH 等）。 */
    private boolean checkDownstreamDatasources = true;

    /** 单个下游数据源探测超时（毫秒）。 */
    private int datasourceCheckTimeoutMs = 3000;

    /** 下游数据源健康检查结果缓存 TTL（秒），避免每次探活都 ping 全量数据源。 */
    private int datasourceCacheTtlSeconds = 60;
}
