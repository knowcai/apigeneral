package com.apigateway.service;

import com.apigateway.entity.Datasource;
import com.apigateway.entity.DatasourceType;
import com.apigateway.exception.BusinessException;

import java.util.Map;

public final class JdbcUrlBuilder {

    private JdbcUrlBuilder() {
    }

    public static String build(Datasource ds) {
        Map<String, Object> params = ds.getDefaultParams() != null ? ds.getDefaultParams() : Map.of();
        return switch (ds.getType()) {
            case DORIS -> buildDoris(ds, params);
            case CLICKHOUSE -> buildClickHouse(ds, params);
        };
    }

    private static String buildDoris(Datasource ds, Map<String, Object> params) {
        String protocol = stringParam(params, "protocol", "mysql");
        if ("http".equalsIgnoreCase(protocol)) {
            return "jdbc:mysql://" + ds.getHost() + ":" + ds.getPort() + "/" + ds.getDatabaseName()
                    + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8";
        }
        return "jdbc:mysql://" + ds.getHost() + ":" + ds.getPort() + "/" + ds.getDatabaseName()
                + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8";
    }

    private static String buildClickHouse(Datasource ds, Map<String, Object> params) {
        String protocol = stringParam(params, "protocol", "http");
        if ("native".equalsIgnoreCase(protocol)) {
            return "jdbc:ch://" + ds.getHost() + ":" + ds.getPort() + "/" + ds.getDatabaseName();
        }
        return "jdbc:ch:http://" + ds.getHost() + ":" + ds.getPort() + "/" + ds.getDatabaseName()
                + "?compress=" + stringParam(params, "compress", "true");
    }

    public static String driverClass(DatasourceType type) {
        return switch (type) {
            case DORIS -> "com.mysql.cj.jdbc.Driver";
            case CLICKHOUSE -> "com.clickhouse.jdbc.ClickHouseDriver";
        };
    }

    private static String stringParam(Map<String, Object> params, String key, String defaultValue) {
        Object value = params.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    public static void assertReadOnly(String sql) {
        String normalized = sql.trim().toUpperCase();
        if (!normalized.startsWith("SELECT") && !normalized.startsWith("WITH") && !normalized.startsWith("SHOW")
                && !normalized.startsWith("DESC") && !normalized.startsWith("EXPLAIN")) {
            throw new BusinessException("仅允许只读查询（SELECT/WITH/SHOW/DESC/EXPLAIN）");
        }
    }
}
