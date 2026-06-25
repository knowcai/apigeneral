package com.apigateway.datasource;

import com.apigateway.entity.Datasource;
import com.apigateway.entity.DatasourceType;

import java.util.Map;

/**
 * 数据源引擎插件：新增引擎时实现此接口并注册为 Spring Bean 即可。
 */
public interface DatasourceDriver {

    DatasourceType type();

    String displayName();

    String buildJdbcUrl(Datasource ds);

    String driverClassName();

    /** 在原始 SQL 后追加分页子句。 */
    String wrapPagedSql(String sql, int offset, int limit);

    /** 健康检查 / 连接测试用 SQL。 */
    String healthCheckSql();

    /** 管理台新建连接串时的默认参数模板。 */
    Map<String, Object> defaultParams();
}
