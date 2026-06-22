package com.apigateway.service;

import com.apigateway.entity.Datasource;
import com.apigateway.exception.BusinessException;
import com.apigateway.repository.DatasourceRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class ConnectionPoolManager {

    private final DatasourceRepository datasourceRepository;
    private final Map<Long, HikariDataSource> pools = new ConcurrentHashMap<>();

    public Connection getConnection(Long datasourceId) throws SQLException {
        return poolFor(datasourceId).getConnection();
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
            try (Connection conn = pool.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT 1");
                 ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getInt(1) != 1) {
                    throw new BusinessException("连接测试失败: SELECT 1 无有效结果");
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
        Map<String, Object> params = ds.getDefaultParams() != null ? ds.getDefaultParams() : Map.of();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(JdbcUrlBuilder.build(ds));
        config.setDriverClassName(JdbcUrlBuilder.driverClass(ds.getType()));
        config.setUsername(ds.getUsername());
        config.setPassword(ds.getPassword());
        config.setMaximumPoolSize(intParam(params, "pool.maxActive", 10));
        config.setMinimumIdle(intParam(params, "pool.minIdle", 2));
        config.setConnectionTimeout(longParam(params, "connectTimeoutMs", 5000L));
        String poolName = ds.getId() != null ? "ds-" + ds.getId() : "ds-test-" + System.nanoTime();
        config.setPoolName(poolName);
        return new HikariDataSource(config);
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
}
