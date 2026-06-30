package com.apigateway.util;

import java.util.Map;

public final class ResponseConfigHelper {

    private ResponseConfigHelper() {
    }

    public static int intConfig(Map<String, Object> config, String key, int defaultValue) {
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
