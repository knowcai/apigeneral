package com.apigateway.health;

import com.apigateway.config.GatewayHealthProperties;
import com.apigateway.datasource.DatasourceDriverRegistry;
import com.apigateway.entity.Datasource;
import com.apigateway.repository.DatasourceRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component("downstreamDatasources")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "gateway.health.check-downstream-datasources", havingValue = "true", matchIfMissing = true)
public class DownstreamDatasourcesHealthIndicator implements HealthIndicator {

    private final DatasourceRepository datasourceRepository;
    private final DatasourceDriverRegistry driverRegistry;
    private final GatewayHealthProperties healthProperties;

    private volatile CachedHealth cached;

    @Override
    public Health health() {
        CachedHealth snapshot = cached;
        long ttlMs = healthProperties.getDatasourceCacheTtlSeconds() * 1000L;
        if (snapshot != null && Instant.now().toEpochMilli() - snapshot.checkedAtMs() < ttlMs) {
            return snapshot.health();
        }
        CachedHealth fresh = buildHealth();
        cached = fresh;
        return fresh.health();
    }

    private CachedHealth buildHealth() {
        List<Datasource> active = datasourceRepository.findByStatus("ACTIVE");
        if (active.isEmpty()) {
            return new CachedHealth(Instant.now().toEpochMilli(),
                    Health.up().withDetail("message", "无已启用的下游数据源").build());
        }

        Map<String, Object> details = new LinkedHashMap<>();
        boolean allUp = true;
        for (Datasource ds : active) {
            String key = ds.getName() + " (" + ds.getType() + ")";
            try {
                ping(ds);
                details.put(key, "UP");
            } catch (Exception e) {
                allUp = false;
                details.put(key, "DOWN: " + e.getMessage());
            }
        }
        Health.Builder builder = allUp ? Health.up() : Health.down();
        builder.withDetail("cachedTtlSec", healthProperties.getDatasourceCacheTtlSeconds());
        return new CachedHealth(Instant.now().toEpochMilli(), builder.withDetails(details).build());
    }

    private void ping(Datasource ds) throws Exception {
        var driver = driverRegistry.require(ds.getType());
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(driver.buildJdbcUrl(ds));
        config.setDriverClassName(driver.driverClassName());
        config.setUsername(ds.getUsername());
        config.setPassword(ds.getPassword());
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(healthProperties.getDatasourceCheckTimeoutMs());
        config.setPoolName("health-" + ds.getId());
        try (HikariDataSource pool = new HikariDataSource(config);
             Connection conn = pool.getConnection();
             PreparedStatement ps = conn.prepareStatement(driver.healthCheckSql())) {
            ps.setQueryTimeout(Math.max(1, healthProperties.getDatasourceCheckTimeoutMs() / 1000));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("health check returned no row");
                }
            }
        }
    }

    private record CachedHealth(long checkedAtMs, Health health) {
    }
}
