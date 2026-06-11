package com.apigateway.service;

import com.apigateway.entity.GatewayPolicy;
import com.apigateway.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.Map;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class GatewayProtectionService {

    private final GatewayPolicyService policyService;
    private final SlidingWindowRateLimiter rateLimiter;
    private final CircuitBreakerService circuitBreakerService;

    public void checkRateLimit(String clientIp, String apiCode, Integer apiQpsOverride) {
        GatewayPolicy policy = policyService.get();
        if (Boolean.TRUE.equals(policy.getGlobalQpsEnabled())
                && !rateLimiter.tryAcquire("global", policy.getGlobalQps())) {
            throw new BusinessException(429, "全局 QPS 超限，请稍后重试");
        }
        if (Boolean.TRUE.equals(policy.getIpQpsEnabled())
                && !rateLimiter.tryAcquire("ip:" + clientIp, policy.getIpQps())) {
            throw new BusinessException(429, "单 IP QPS 超限，请稍后重试");
        }
        int apiLimit = apiQpsOverride != null && apiQpsOverride > 0 ? apiQpsOverride : policy.getApiQps();
        if (Boolean.TRUE.equals(policy.getApiQpsEnabled())
                && !rateLimiter.tryAcquire("api:" + apiCode, apiLimit)) {
            throw new BusinessException(429, "接口 QPS 超限，请稍后重试");
        }
    }

    public Object checkCircuitOrFallback(String apiCode) {
        GatewayPolicy policy = policyService.get();
        if (circuitBreakerService.allowRequest(apiCode, policy)) {
            return null;
        }
        Object fallback = circuitBreakerService.parseFallback(policy);
        throw new BusinessException(503, "服务熔断中", fallback);
    }

    public void onSuccess(String apiCode) {
        circuitBreakerService.recordSuccess(apiCode);
    }

    public void onFailure(String apiCode) {
        circuitBreakerService.recordFailure(apiCode, policyService.get());
    }

    public <T> T executeWithRetry(Supplier<T> action) {
        GatewayPolicy policy = policyService.get();
        int maxAttempts = Boolean.TRUE.equals(policy.getRetryEnabled()) ? policy.getRetryMaxAttempts() + 1 : 1;
        long interval = policy.getRetryIntervalMs();

        RuntimeException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return action.get();
            } catch (BusinessException e) {
                throw e;
            } catch (RuntimeException e) {
                last = e;
                if (!isRetryable(e) || attempt >= maxAttempts) {
                    throw e;
                }
                sleep(interval);
            }
        }
        throw last != null ? last : new BusinessException("执行失败");
    }

    private boolean isRetryable(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            if (cur instanceof SQLException) {
                return true;
            }
            String msg = cur.getMessage();
            if (msg != null && (msg.contains("timeout") || msg.contains("Timeout")
                    || msg.contains("Connection") || msg.contains("communications link"))) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("重试被中断");
        }
    }

    @SuppressWarnings("unchecked")
    public Integer resolveApiQpsOverride(Map<String, Object> responseConfig) {
        if (responseConfig == null || !responseConfig.containsKey("apiQps")) {
            return null;
        }
        Object v = responseConfig.get("apiQps");
        if (v instanceof Number n) {
            return n.intValue();
        }
        return null;
    }
}
