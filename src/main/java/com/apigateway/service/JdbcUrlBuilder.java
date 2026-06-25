package com.apigateway.service;

import com.apigateway.exception.BusinessException;

public final class JdbcUrlBuilder {

    private JdbcUrlBuilder() {
    }

    public static void assertReadOnly(String sql) {
        String normalized = sql.trim().toUpperCase();
        if (!normalized.startsWith("SELECT") && !normalized.startsWith("WITH") && !normalized.startsWith("SHOW")
                && !normalized.startsWith("DESC") && !normalized.startsWith("EXPLAIN")) {
            throw new BusinessException("仅允许只读查询（SELECT/WITH/SHOW/DESC/EXPLAIN）");
        }
    }
}

