package com.apigateway.service;

import com.apigateway.exception.BusinessException;

import java.util.regex.Pattern;

public final class SqlSecurityValidator {

    private static final Pattern FORBIDDEN = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|TRUNCATE|CREATE|GRANT|REVOKE|EXEC|EXECUTE|MERGE|REPLACE|CALL)\\b",
            Pattern.CASE_INSENSITIVE);

    private SqlSecurityValidator() {
    }

    public static void validateReadOnlySql(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new BusinessException("SQL 模板不能为空");
        }
        String normalized = sql.trim();
        if (!normalized.regionMatches(true, 0, "SELECT", 0, 6)
                && !normalized.regionMatches(true, 0, "WITH", 0, 4)
                && !normalized.regionMatches(true, 0, "SHOW", 0, 4)
                && !normalized.regionMatches(true, 0, "DESC", 0, 4)
                && !normalized.regionMatches(true, 0, "EXPLAIN", 0, 7)) {
            throw new BusinessException("仅允许 SELECT/WITH/SHOW/DESC/EXPLAIN 语句");
        }
        if (FORBIDDEN.matcher(normalized).find()) {
            throw new BusinessException("SQL 含禁止关键字，仅允许只读查询");
        }
        if (normalized.contains(";")) {
            throw new BusinessException("SQL 不允许包含分号（禁止多语句）");
        }
    }
}
