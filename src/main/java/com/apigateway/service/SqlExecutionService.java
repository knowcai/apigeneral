package com.apigateway.service;

import com.apigateway.dto.QueryResult;
import com.apigateway.entity.Datasource;
import com.apigateway.entity.ResponseMode;
import com.apigateway.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SqlExecutionService {

    private final ConnectionPoolManager connectionPoolManager;

    public QueryResult execute(Datasource datasource, String sqlTemplate, Map<String, Object> params,
                               Map<String, Object> responseConfig, int page, int pageSize) {
        JdbcUrlBuilder.assertReadOnly(sqlTemplate);
        SqlTemplateEngine.ParsedSql parsed = SqlTemplateEngine.parse(sqlTemplate, params);
        List<Object> values = SqlTemplateEngine.bindValues(parsed, params);

        int effectivePageSize = Math.min(pageSize, intConfig(responseConfig, "maxPageSize", 500));
        int offset = Math.max(0, page - 1) * effectivePageSize;
        assertWithinLimits(responseConfig, offset);

        String pagedSql = wrapPagedSql(datasource, parsed.sql(), offset, effectivePageSize);
        int timeoutSec = intConfig(responseConfig, "timeoutSec", 60);

        try (Connection conn = connectionPoolManager.getConnection(datasource.getId());
             PreparedStatement ps = conn.prepareStatement(pagedSql)) {
            if (timeoutSec > 0) {
                ps.setQueryTimeout(timeoutSec);
            }
            bind(ps, values);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> rows = mapRows(rs);
                boolean hasMore = rows.size() >= effectivePageSize;
                return QueryResult.builder()
                        .rows(rows)
                        .page(page)
                        .pageSize(effectivePageSize)
                        .hasMore(hasMore)
                        .build();
            }
        } catch (SQLException e) {
            if (isQueryTimeout(e)) {
                throw new BusinessException("查询超时，超过 " + timeoutSec + " 秒限制");
            }
            throw new BusinessException("SQL 执行失败: " + e.getMessage());
        }
    }

    private boolean isQueryTimeout(SQLException e) {
        Throwable cur = e;
        while (cur != null) {
            if (cur instanceof SQLTimeoutException) {
                return true;
            }
            String msg = cur.getMessage();
            if (msg != null && (msg.contains("timeout") || msg.contains("Timeout") || msg.contains("timed out"))) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    private void assertWithinLimits(Map<String, Object> config, int offset) {
        int maxOffset = intConfig(config, "maxOffset", 100000);
        if (offset >= maxOffset) {
            throw new BusinessException("已超过最大偏移量 " + maxOffset + "，请缩小分页");
        }
    }

    private String wrapPagedSql(Datasource ds, String sql, int offset, int limit) {
        return switch (ds.getType()) {
            case DORIS -> sql + " LIMIT " + limit + " OFFSET " + offset;
            case CLICKHOUSE -> sql + " LIMIT " + offset + ", " + limit;
        };
    }

    private List<Map<String, Object>> mapRows(ResultSet rs) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        ResultSetMetaData meta = rs.getMetaData();
        int cols = meta.getColumnCount();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= cols; i++) {
                row.put(meta.getColumnLabel(i), rs.getObject(i));
            }
            rows.add(row);
        }
        return rows;
    }

    private void bind(PreparedStatement ps, List<Object> values) throws SQLException {
        for (int i = 0; i < values.size(); i++) {
            ps.setObject(i + 1, values.get(i));
        }
    }

    private int intConfig(Map<String, Object> config, String key, int defaultValue) {
        if (config == null) {
            return defaultValue;
        }
        Object v = config.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        return defaultValue;
    }
}
