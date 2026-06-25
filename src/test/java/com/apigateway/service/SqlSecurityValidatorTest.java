package com.apigateway.service;

import com.apigateway.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SqlSecurityValidatorTest {

    @Test
    void acceptsSelect() {
        assertDoesNotThrow(() -> SqlSecurityValidator.validateReadOnlySql("SELECT * FROM t WHERE id = :id"));
    }

    @Test
    void acceptsWithClause() {
        assertDoesNotThrow(() -> SqlSecurityValidator.validateReadOnlySql(
                "WITH cte AS (SELECT 1 AS x) SELECT * FROM cte"));
    }

    @Test
    void rejectsDelete() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> SqlSecurityValidator.validateReadOnlySql("DELETE FROM t"));
        assertTrue(ex.getMessage().contains("SELECT") || ex.getMessage().contains("解析"));
    }

    @Test
    void rejectsSemicolon() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> SqlSecurityValidator.validateReadOnlySql("SELECT 1; SELECT 2"));
        assertTrue(ex.getMessage().contains("分号"));
    }

    @Test
    void rejectsInsertSubqueryAttack() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> SqlSecurityValidator.validateReadOnlySql("INSERT INTO t VALUES (1)"));
        assertNotNull(ex.getMessage());
    }
}
