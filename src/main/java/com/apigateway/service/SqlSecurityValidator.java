package com.apigateway.service;

import com.apigateway.exception.BusinessException;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Select;

public final class SqlSecurityValidator {

    private SqlSecurityValidator() {
    }

    public static void validateReadOnlySql(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new BusinessException("SQL 模板不能为空");
        }
        String normalized = sql.trim();
        if (normalized.contains(";")) {
            throw new BusinessException("SQL 不允许包含分号（禁止多语句）");
        }
        try {
            Statements statements = CCJSqlParserUtil.parseStatements(normalized);
            if (statements == null || statements.isEmpty()) {
                throw new BusinessException("无法解析 SQL 模板");
            }
            if (statements.size() > 1) {
                throw new BusinessException("SQL 不允许包含多条语句");
            }
            Statement statement = statements.get(0);
            if (!(statement instanceof Select)) {
                throw new BusinessException("仅允许 SELECT 只读查询（解析器校验）");
            }
        } catch (JSQLParserException e) {
            if (isAllowedNonSelectPrefix(normalized)) {
                validateLegacyPrefix(normalized);
                return;
            }
            throw new BusinessException("SQL 解析失败，请检查语法: " + e.getMessage());
        }
    }

    private static boolean isAllowedNonSelectPrefix(String sql) {
        return sql.regionMatches(true, 0, "SHOW", 0, 4)
                || sql.regionMatches(true, 0, "DESC", 0, 4)
                || sql.regionMatches(true, 0, "DESCRIBE", 0, 8)
                || sql.regionMatches(true, 0, "EXPLAIN", 0, 7);
    }

    private static void validateLegacyPrefix(String sql) {
        if (!isAllowedNonSelectPrefix(sql)) {
            throw new BusinessException("仅允许 SELECT/WITH/SHOW/DESC/EXPLAIN 语句");
        }
    }
}
