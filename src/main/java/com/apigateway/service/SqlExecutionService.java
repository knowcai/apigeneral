package com.apigateway.service;

import com.apigateway.dto.QueryResult;
import com.apigateway.entity.Datasource;
import com.apigateway.entity.ResponseMode;
import com.apigateway.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SqlExecutionService {

    private final ConnectionPoolManager connectionPoolManager;

    public QueryResult execute(Datasource datasource, String sqlTemplate, Map<String, Object> params,
                               ResponseMode mode, Map<String, Object> responseConfig, int page, int pageSize,
                               int chunkIndex) {
        JdbcUrlBuilder.assertReadOnly(sqlTemplate);
        SqlTemplateEngine.ParsedSql parsed = SqlTemplateEngine.parse(sqlTemplate, params);
        List<Object> values = SqlTemplateEngine.bindValues(parsed, params);

        int effectivePageSize = resolvePageSize(mode, responseConfig, pageSize);
        int offset = mode == ResponseMode.CHUNK ? chunkIndex * effectivePageSize : Math.max(0, page - 1) * effectivePageSize;
        assertWithinLimits(mode, responseConfig, offset, effectivePageSize);

        String pagedSql = wrapPagedSql(datasource, parsed.sql(), offset, effectivePageSize);

        try (Connection conn = connectionPoolManager.getConnection(datasource.getId());
             PreparedStatement ps = conn.prepareStatement(pagedSql)) {
            bind(ps, values);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> rows = mapRows(rs);
                boolean hasMore = rows.size() >= effectivePageSize;
                return QueryResult.builder()
                        .rows(rows)
                        .page(mode == ResponseMode.PAGE ? page : chunkIndex + 1)
                        .pageSize(effectivePageSize)
                        .hasMore(hasMore)
                        .chunkIndex(chunkIndex)
                        .chunkToken(hasMore ? UUID.randomUUID().toString() : null)
                        .build();
            }
        } catch (SQLException e) {
            throw new BusinessException("SQL 执行失败: " + e.getMessage());
        }
    }

    public StreamingResponseBody stream(Datasource datasource, String sqlTemplate, Map<String, Object> params,
                                        Map<String, Object> responseConfig) {
        JdbcUrlBuilder.assertReadOnly(sqlTemplate);
        SqlTemplateEngine.ParsedSql parsed = SqlTemplateEngine.parse(sqlTemplate, params);
        List<Object> values = SqlTemplateEngine.bindValues(parsed, params);
        int batchSize = intConfig(responseConfig, "streamBatchSize", 500);
        int maxStreamRows = intConfig(responseConfig, "maxStreamRows", 100000);
        long maxDurationMs = intConfig(responseConfig, "maxStreamDurationSec", 300) * 1000L;

        return outputStream -> {
            long startMs = System.currentTimeMillis();
            try (Connection conn = connectionPoolManager.getConnection(datasource.getId());
                 PreparedStatement ps = conn.prepareStatement(parsed.sql())) {
                bind(ps, values);
                try (ResultSet rs = ps.executeQuery();
                     PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int count = 0;
                    while (rs.next()) {
                        if (count >= maxStreamRows) {
                            break;
                        }
                        if (System.currentTimeMillis() - startMs > maxDurationMs) {
                            break;
                        }
                        writer.println(toJsonLine(rs, meta));
                        count++;
                        if (count % batchSize == 0) {
                            writer.flush();
                        }
                    }
                    writer.flush();
                }
            } catch (SQLException e) {
                throw new BusinessException("流式查询失败: " + e.getMessage());
            }
        };
    }

    private void assertWithinLimits(ResponseMode mode, Map<String, Object> config, int offset, int pageSize) {
        if (mode == ResponseMode.PAGE) {
            int maxOffset = intConfig(config, "maxOffset", 100000);
            if (offset >= maxOffset) {
                throw new BusinessException("已超过最大偏移量 " + maxOffset + "，请缩小分页或使用分块/流式模式");
            }
        }
        if (mode == ResponseMode.CHUNK) {
            int maxTotalRows = intConfig(config, "maxTotalRows", 500000);
            if (offset >= maxTotalRows) {
                throw new BusinessException("已超过分批累计上限 " + maxTotalRows + " 行");
            }
            if (offset + pageSize > maxTotalRows) {
                throw new BusinessException("本批将超出累计上限 " + maxTotalRows + " 行，请减小每批条数");
            }
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

    private int resolvePageSize(ResponseMode mode, Map<String, Object> config, int requested) {
        int max = intConfig(config, "maxPageSize", 500);
        int defaultSize = intConfig(config, "defaultPageSize", 20);
        if (mode == ResponseMode.CHUNK) {
            defaultSize = intConfig(config, "chunkSize", 1000);
            max = intConfig(config, "maxChunkSize", 10000);
        }
        int size = requested > 0 ? requested : defaultSize;
        return Math.min(size, max);
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

    private String toJsonLine(ResultSet rs, ResultSetMetaData meta) throws SQLException {
        StringBuilder sb = new StringBuilder("{");
        int cols = meta.getColumnCount();
        for (int i = 1; i <= cols; i++) {
            if (i > 1) {
                sb.append(',');
            }
            sb.append('"').append(meta.getColumnLabel(i)).append("\":");
            Object val = rs.getObject(i);
            if (val == null) {
                sb.append("null");
            } else if (val instanceof Number) {
                sb.append(val);
            } else {
                sb.append('"').append(String.valueOf(val).replace("\"", "\\\"")).append('"');
            }
        }
        sb.append('}');
        return sb.toString();
    }
}
