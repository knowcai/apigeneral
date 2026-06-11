package com.apigateway.service;

import com.apigateway.entity.Datasource;
import com.apigateway.exception.BusinessException;
import com.apigateway.repository.DatasourceRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.sql.Connection;
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
        try (Connection conn = createPool(ds).getConnection()) {
            return conn.isValid(3);
        } catch (SQLException e) {
            throw new BusinessException("连接测试失败: " + e.getMessage());
        }
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
        config.setPoolName("ds-" + ds.getId());
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
