package com.apigateway.datasource;

import com.apigateway.entity.Datasource;
import com.apigateway.entity.DatasourceType;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class TrinoDatasourceDriver implements DatasourceDriver {

    @Override
    public DatasourceType type() {
        return DatasourceType.TRINO;
    }

    @Override
    public String displayName() {
        return "Trino";
    }

    @Override
    public String buildJdbcUrl(Datasource ds) {
        Map<String, Object> params = DatasourceParamSupport.params(ds);
        String catalog = ds.getDatabaseName();
        String schema = DatasourceParamSupport.stringParam(params, "schema", "default");
        return "jdbc:trino://" + ds.getHost() + ":" + ds.getPort() + "/" + catalog + "/" + schema;
    }

    @Override
    public String driverClassName() {
        return "io.trino.jdbc.TrinoDriver";
    }

    @Override
    public String wrapPagedSql(String sql, int offset, int limit) {
        return sql + " OFFSET " + offset + " LIMIT " + limit;
    }

    @Override
    public String healthCheckSql() {
        return "SELECT 1";
    }

    @Override
    public Map<String, Object> defaultParams() {
        Map<String, Object> template = new HashMap<>(DatasourceParamSupport.basePoolDefaults());
        template.put("schema", "default");
        template.put("queryTimeoutSec", 120);
        return template;
    }
}
