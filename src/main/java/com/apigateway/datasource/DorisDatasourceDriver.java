package com.apigateway.datasource;

import com.apigateway.entity.Datasource;
import com.apigateway.entity.DatasourceType;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class DorisDatasourceDriver implements DatasourceDriver {

    @Override
    public DatasourceType type() {
        return DatasourceType.DORIS;
    }

    @Override
    public String displayName() {
        return "Apache Doris (MySQL 协议)";
    }

    @Override
    public String buildJdbcUrl(Datasource ds) {
        return "jdbc:mysql://" + ds.getHost() + ":" + ds.getPort() + "/" + ds.getDatabaseName()
                + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8";
    }

    @Override
    public String driverClassName() {
        return "com.mysql.cj.jdbc.Driver";
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
        template.put("protocol", "mysql");
        template.put("queryTimeoutSec", 300);
        return template;
    }
}
