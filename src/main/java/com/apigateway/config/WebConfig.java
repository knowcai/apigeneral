package com.apigateway.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final GatewaySecurityProperties securityProperties;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        var origins = securityProperties.getCorsAllowedOrigins();
        if (origins == null || origins.isEmpty()) {
            return;
        }
        registry.addMapping("/**")
                .allowedOrigins(origins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("Authorization", "Content-Type", "X-Api-Key", "X-Request-Id", "Accept")
                .allowCredentials(securityProperties.isCorsAllowCredentials());
    }
}
