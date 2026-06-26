package com.apigateway.service;

import com.apigateway.datasource.DatasourceDriverRegistry;
import com.apigateway.dto.QueryResult;
import com.apigateway.entity.Datasource;
import com.apigateway.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SqlExecutionService {

    private final ConnectionPoolManager connectionPoolManager;
    private final DatasourceDriverRegistry driverRegistry;

    public QueryResult execute(Datasource datasource, String sqlTemplate, Map<String, Object> params,
                               Map<String, Object> responseConfig, int page, int pageSize) {
        SqlSecurityValidator.validateReadOnlySql(sqlTemplate);
        SqlTemplateEngine.ParsedSql parsed = SqlTemplateEngine.parse(sqlTemplate, params);
        List<Object> values = SqlTemplateEngine.bindValues(parsed, params);

        int effectivePageSize = Math.min(pageSize, intConfig(responseConfig, "maxPageSize", 500));
        int offset = Math.max(0, page - 1) * effectivePageSize;
        assertWithinLimits(responseConfig, offset);

        String pagedSql = driverRegistry.require(datasource.getType())
                .wrapPagedSql(parsed.sql(), offset, effectivePageSize);
        int timeoutSec = intConfig(responseConfig, "timeoutSec", 60);
        long total = countTotal(datasource, parsed.sql(), values, timeoutSec);

        try (Connection conn = connectionPoolManager.getConnection(datasource.getId());
             PreparedStatement ps = conn.prepareStatement(pagedSql)) {
            if (timeoutSec > 0) {
                ps.setQueryTimeout(timeoutSec);
            }
            bind(ps, values);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> rows = mapRows(rs);
                boolean hasMore = (long) offset + rows.size() < total;
                return QueryResult.builder()
                        .rows(rows)
                        .total(total)
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

    /** 管理台试跑：强制 LIMIT 1，不对外暴露。只读数据源仍强制 SELECT 类语句。 */
    public QueryResult executeTest(Datasource datasource, String sqlTemplate, Map<String, Object> params,
                                   int timeoutSec) {
        if (!Boolean.FALSE.equals(datasource.getReadonly())) {
            SqlSecurityValidator.validateReadOnlySql(sqlTemplate);
        }
        SqlTemplateEngine.ParsedSql parsed = SqlTemplateEngine.parse(sqlTemplate, params);
        List<Object> values = SqlTemplateEngine.bindValues(parsed, params);
        String testSql = driverRegistry.require(datasource.getType())
                .wrapPagedSql(parsed.sql(), 0, 1);
        int effectiveTimeout = timeoutSec > 0 ? timeoutSec : 30;

        try (Connection conn = connectionPoolManager.getConnection(datasource.getId());
             PreparedStatement ps = conn.prepareStatement(testSql)) {
            ps.setQueryTimeout(effectiveTimeout);
            bind(ps, values);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> rows = mapRows(rs);
                return QueryResult.builder()
                        .rows(rows)
                        .total(rows.size())
                        .page(1)
                        .pageSize(1)
                        .hasMore(false)
                        .build();
            }
        } catch (SQLException e) {
            if (isQueryTimeout(e)) {
                throw new BusinessException("试跑超时，超过 " + effectiveTimeout + " 秒限制");
            }
            throw new BusinessException("试跑失败: " + e.getMessage());
        }
    }

    private long countTotal(Datasource datasource, String sql, List<Object> values, int timeoutSec) {
        String countSql = "SELECT COUNT(*) FROM (" + sql + ") AS _gw_cnt";
        try (Connection conn = connectionPoolManager.getConnection(datasource.getId());
             PreparedStatement ps = conn.prepareStatement(countSql)) {
            if (timeoutSec > 0) {
                ps.setQueryTimeout(timeoutSec);
            }
            bind(ps, values);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            if (isQueryTimeout(e)) {
                throw new BusinessException("统计总数超时，超过 " + timeoutSec + " 秒限制");
            }
            throw new BusinessException("统计总数失败: " + e.getMessage());
        }
        return 0;
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
