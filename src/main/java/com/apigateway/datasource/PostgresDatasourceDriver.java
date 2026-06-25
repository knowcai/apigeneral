package com.apigateway.datasource;

import com.apigateway.entity.Datasource;
import com.apigateway.entity.DatasourceType;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/** 示例插件：PostgreSQL 只读查询（与元数据库驱动复用）。 */
@Component
public class PostgresDatasourceDriver implements DatasourceDriver {

    @Override
    public DatasourceType type() {
        return DatasourceType.POSTGRES;
    }

    @Override
    public String displayName() {
        return "PostgreSQL (只读)";
    }

    @Override
    public String buildJdbcUrl(Datasource ds) {
        return "jdbc:postgresql://" + ds.getHost() + ":" + ds.getPort() + "/" + ds.getDatabaseName();
    }

    @Override
    public String driverClassName() {
        return "org.postgresql.Driver";
    }

    @Override
    public String wrapPagedSql(String sql, int offset, int limit) {
        return sql + " LIMIT " + limit + " OFFSET " + offset;
    }

    @Override
    public String healthCheckSql() {
        return "SELECT 1";
    }

    @Override
    public Map<String, Object> defaultParams() {
        Map<String, Object> template = new HashMap<>(DatasourceParamSupport.basePoolDefaults());
        template.put("queryTimeoutSec", 120);
        return template;
    }
}
