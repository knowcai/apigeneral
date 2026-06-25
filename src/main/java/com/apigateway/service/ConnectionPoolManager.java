package com.apigateway.service;

import com.apigateway.datasource.DatasourceDriverRegistry;
import com.apigateway.entity.Datasource;
import com.apigateway.exception.BusinessException;
import com.apigateway.metrics.GatewayMetrics;
import com.apigateway.pool.BudgetReleasingConnection;
import com.apigateway.pool.GlobalConnectionBudget;
import com.apigateway.repository.DatasourceRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class ConnectionPoolManager {

    private final DatasourceRepository datasourceRepository;
    private final DatasourceDriverRegistry driverRegistry;
    private final GlobalConnectionBudget globalConnectionBudget;
    private final GatewayMetrics gatewayMetrics;
    private final Map<Long, HikariDataSource> pools = new ConcurrentHashMap<>();

    public Connection getConnection(Long datasourceId) throws SQLException {
        globalConnectionBudget.acquire();
        try {
            Connection raw = poolFor(datasourceId).getConnection();
            return BudgetReleasingConnection.wrap(raw, globalConnectionBudget::release);
        } catch (SQLException e) {
            globalConnectionBudget.release();
            throw e;
        }
    }

    public void evict(Long datasourceId) {
        HikariDataSource ds = pools.remove(datasourceId);
        if (ds != null) {
            ds.close();
        }
    }

    public boolean testConnection(Datasource ds) {
        HikariDataSource pool = null;
        try {
            pool = createPool(ds);
            String checkSql = driverRegistry.require(ds.getType()).healthCheckSql();
            try (Connection conn = pool.getConnection();
                 PreparedStatement ps = conn.prepareStatement(checkSql);
                 ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new BusinessException("连接测试失败: 健康检查无有效结果");
                }
                return true;
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Throwable e) {
            throw new BusinessException("连接测试失败: " + rootMessage(e));
        } finally {
            if (pool != null) {
                try {
                    pool.close();
                } catch (Exception ignored) {
                    // ignore
                }
            }
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable cur = e;
        String msg = e.getMessage();
        while (cur.getCause() != null) {
            cur = cur.getCause();
            if (cur.getMessage() != null && !cur.getMessage().isBlank()) {
                msg = cur.getMessage();
            }
        }
        return msg != null ? msg : e.getClass().getSimpleName();
    }

    private HikariDataSource poolFor(Long datasourceId) {
        return pools.computeIfAbsent(datasourceId, id -> {
            Datasource ds = datasourceRepository.findById(id)
                    .orElseThrow(() -> new BusinessException("数据源不存在: " + id));
            return createPool(ds);
        });
    }

    private HikariDataSource createPool(Datasource ds) {
        var driver = driverRegistry.require(ds.getType());
        Map<String, Object> params = ds.getDefaultParams() != null ? ds.getDefaultParams() : Map.of();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(driver.buildJdbcUrl(ds));
        config.setDriverClassName(driver.driverClassName());
        config.setUsername(ds.getUsername());
        config.setPassword(ds.getPassword());
        config.setMaximumPoolSize(intParam(params, "pool.maxActive", 10));
        config.setMinimumIdle(intParam(params, "pool.minIdle", 2));
        config.setConnectionTimeout(longParam(params, "connectTimeoutMs", 5000L));
        String poolName = ds.getId() != null ? "ds-" + ds.getId() : "ds-test-" + System.nanoTime();
        config.setPoolName(poolName);
        config.setRegisterMbeans(true);
        HikariDataSource dataSource = new HikariDataSource(config);
        gatewayMetrics.registerHikariPool(poolName, dataSource);
        return dataSource;
    }

    private int intParam(Map<String, Object> params, String key, int defaultValue) {
        Object v = params.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        return defaultValue;
    }

    private long longParam(Map<String, Object> params, String key, long defaultValue) {
        Object v = params.get(key);
        if (v instanceof Number n) {
            return n.longValue();
        }
        return defaultValue;
    }

    /** 当前已创建的数据源连接池实时状态（供监控大盘展示）。 */
    public List<Map<String, Object>> poolSnapshots() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<Long, HikariDataSource> entry : pools.entrySet()) {
            Long dsId = entry.getKey();
            HikariDataSource ds = entry.getValue();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("datasourceId", dsId);
            datasourceRepository.findById(dsId).ifPresent(meta -> {
                item.put("datasourceName", meta.getName());
                item.put("datasourceType", meta.getType() != null ? meta.getType().name() : "");
            });
            item.put("poolName", ds.getPoolName());
            var mx = ds.getHikariPoolMXBean();
            if (mx != null) {
                item.put("active", mx.getActiveConnections());
                item.put("idle", mx.getIdleConnections());
                item.put("total", mx.getTotalConnections());
                int max = ds.getMaximumPoolSize();
                item.put("max", max);
                item.put("usagePercent", max > 0 ? Math.round(mx.getActiveConnections() * 1000.0 / max) / 10.0 : 0);
            } else {
                item.put("active", 0);
                item.put("idle", 0);
                item.put("total", 0);
                item.put("max", ds.getMaximumPoolSize());
                item.put("usagePercent", 0);
            }
            list.add(item);
        }
        list.sort(Comparator.comparing(m -> String.valueOf(m.getOrDefault("datasourceName", ""))));
        return list;
    }
}
