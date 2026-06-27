package com.apigateway.security;

import com.apigateway.config.GatewaySecurityProperties;
import com.apigateway.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class LoginRateLimiter {

    private final GatewaySecurityProperties properties;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public LoginRateLimiter(GatewaySecurityProperties properties) {
        this.properties = properties;
    }

    public void check(String clientIp, String username) {
        int limit = Math.max(1, properties.getLoginRateLimitPerMinute());
        String key = (clientIp != null ? clientIp : "unknown") + "|" + (username != null ? username.toLowerCase() : "");
        long now = System.currentTimeMillis();
        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.startMs >= 60_000L) {
                return new Window(now, new AtomicInteger(0));
            }
            return existing;
        });
        if (window.count.incrementAndGet() > limit) {
            throw new BusinessException(429, "登录尝试过于频繁，请稍后再试");
        }
    }

    private record Window(long startMs, AtomicInteger count) {
    }
}
