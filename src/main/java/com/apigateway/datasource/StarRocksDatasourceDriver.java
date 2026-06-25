package com.apigateway.datasource;

import com.apigateway.entity.Datasource;
import com.apigateway.entity.DatasourceType;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/** StarRocks 使用 MySQL 协议，分页语法与 Doris 相同。 */
@Component
public class StarRocksDatasourceDriver implements DatasourceDriver {

    @Override
    public DatasourceType type() {
        return DatasourceType.STARROCKS;
    }

    @Override
    public String displayName() {
        return "StarRocks (MySQL 协议)";
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
