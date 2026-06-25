package com.apigateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gateway.redis")
public class GatewayRedisProperties {

    /** 开启后限流、熔断、进行中请求计数使用 Redis，支持多实例部署。 */
    private boolean enabled = false;

    private String host = "localhost";

    private int port = 6379;

    private String password = "";
}
