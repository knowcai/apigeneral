package com.apigateway.metrics;

import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
public class GatewayMetrics {

    private final MeterRegistry registry;
    private final Set<String> registeredPools = ConcurrentHashMap.newKeySet();

    public GatewayMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordApiRequest(String apiCode, String status, long durationMs) {
        Counter.builder("gateway.api.requests")
                .tag("api_code", apiCode)
                .tag("status", status)
                .register(registry)
                .increment();
        Timer.builder("gateway.api.request.duration")
                .tag("api_code", apiCode)
                .tag("status", status)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void registerHikariPool(String poolName, HikariDataSource dataSource) {
        if (!registeredPools.add(poolName)) {
            return;
        }
        Gauge.builder("gateway.datasource.pool.active", dataSource,
                        ds -> poolMx(ds) != null ? poolMx(ds).getActiveConnections() : 0)
                .tag("pool", poolName)
                .register(registry);
        Gauge.builder("gateway.datasource.pool.idle", dataSource,
                        ds -> poolMx(ds) != null ? poolMx(ds).getIdleConnections() : 0)
                .tag("pool", poolName)
                .register(registry);
        Gauge.builder("gateway.datasource.pool.total", dataSource,
                        ds -> poolMx(ds) != null ? poolMx(ds).getTotalConnections() : 0)
                .tag("pool", poolName)
                .register(registry);
    }

    private static com.zaxxer.hikari.HikariPoolMXBean poolMx(HikariDataSource ds) {
        try {
            return ds.getHikariPoolMXBean();
        } catch (Exception e) {
            return null;
        }
    }
}
