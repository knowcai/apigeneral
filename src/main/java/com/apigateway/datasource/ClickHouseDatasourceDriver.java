package com.apigateway.datasource;

import com.apigateway.entity.Datasource;
import com.apigateway.entity.DatasourceType;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class ClickHouseDatasourceDriver implements DatasourceDriver {

    @Override
    public DatasourceType type() {
        return DatasourceType.CLICKHOUSE;
    }

    @Override
    public String displayName() {
        return "ClickHouse";
    }

    @Override
    public String buildJdbcUrl(Datasource ds) {
        Map<String, Object> params = DatasourceParamSupport.params(ds);
        String protocol = DatasourceParamSupport.stringParam(params, "protocol", "http");
        if ("native".equalsIgnoreCase(protocol)) {
            return "jdbc:ch://" + ds.getHost() + ":" + ds.getPort() + "/" + ds.getDatabaseName();
        }
        return "jdbc:ch:http://" + ds.getHost() + ":" + ds.getPort() + "/" + ds.getDatabaseName()
                + "?compress=" + DatasourceParamSupport.stringParam(params, "compress", "true");
    }

    @Override
    public String driverClassName() {
        return "com.clickhouse.jdbc.ClickHouseDriver";
    }

    @Override
    public String wrapPagedSql(String sql, int offset, int limit) {
        return sql + " LIMIT " + offset + ", " + limit;
    }

    @Override
    public String healthCheckSql() {
        return "SELECT 1";
    }

    @Override
    public Map<String, Object> defaultParams() {
        Map<String, Object> template = new HashMap<>(DatasourceParamSupport.basePoolDefaults());
        template.put("protocol", "http");
        template.put("compress", true);
        template.put("maxThreads", 4);
        return template;
    }
}
