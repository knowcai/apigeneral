package com.apigateway.datasource;

import com.apigateway.entity.Datasource;

import java.util.HashMap;
import java.util.Map;

public final class DatasourceParamSupport {

    private DatasourceParamSupport() {
    }

    public static Map<String, Object> params(Datasource ds) {
        return ds.getDefaultParams() != null ? ds.getDefaultParams() : Map.of();
    }

    public static Map<String, Object> basePoolDefaults() {
        Map<String, Object> template = new HashMap<>();
        template.put("pool.minIdle", 2);
        template.put("pool.maxActive", 10);
        template.put("connectTimeoutMs", 5000);
        return template;
    }

    public static String stringParam(Map<String, Object> params, String key, String defaultValue) {
        Object value = params.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }
}
