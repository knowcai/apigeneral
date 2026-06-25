package com.apigateway.pool;

import com.apigateway.config.GatewayPoolProperties;
import com.apigateway.exception.BusinessException;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class GlobalConnectionBudget {

    private final Semaphore semaphore;
    private final int maxConnections;
    private final long acquireTimeoutMs;

    public GlobalConnectionBudget(GatewayPoolProperties properties, MeterRegistry meterRegistry) {
        int max = Math.max(1, properties.getGlobalMaxConnections());
        this.maxConnections = max;
        this.semaphore = new Semaphore(max, true);
        this.acquireTimeoutMs = properties.getAcquireTimeoutMs();
        Gauge.builder("gateway.pool.global.max", () -> max).register(meterRegistry);
        Gauge.builder("gateway.pool.global.available", semaphore::availablePermits).register(meterRegistry);
        Gauge.builder("gateway.pool.global.in_use", () -> max - semaphore.availablePermits()).register(meterRegistry);
    }

    public void acquire() {
        try {
            if (!semaphore.tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS)) {
                throw new BusinessException(503, "网关全局连接池已满，请稍后重试");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(503, "获取连接被中断");
        }
    }

    public void release() {
        semaphore.release();
    }

    public Map<String, Object> snapshot() {
        int available = semaphore.availablePermits();
        int inUse = maxConnections - available;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("max", maxConnections);
        m.put("inUse", inUse);
        m.put("available", available);
        m.put("usagePercent", maxConnections > 0 ? Math.round(inUse * 1000.0 / maxConnections) / 10.0 : 0);
        return m;
    }
}
