package com.apigateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gateway.security")
public class GatewaySecurityProperties {

    /** 是否公开 Swagger / OpenAPI 文档（生产应 false）。 */
    private boolean swaggerPublic = true;

    /** 是否公开 Actuator 端点（生产应 false，health 可经反向代理内网访问）。 */
    private boolean actuatorPublic = true;

    /** CORS 允许的来源；生产勿使用 *。 */
    private List<String> corsAllowedOrigins = new ArrayList<>(List.of(
            "http://localhost:5173",
            "http://localhost:8080",
            "http://localhost:8088"));

    private boolean corsAllowCredentials = true;

    /** 仅在反向代理后且已剥离客户端 XFF 时启用。 */
    private boolean trustForwardedFor = false;

    /** 登录接口每 IP+用户名 每分钟最大尝试次数。 */
    private int loginRateLimitPerMinute = 20;

    /** JWT HttpOnly Cookie 名称。 */
    private String jwtCookieName = "gw_token";

    /** 生产环境 Cookie Secure 标志。 */
    private boolean jwtCookieSecure = false;
}
