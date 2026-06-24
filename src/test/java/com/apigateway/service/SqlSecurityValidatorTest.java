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
    void rejectsDelete() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> SqlSecurityValidator.validateReadOnlySql("DELETE FROM t"));
        assertTrue(ex.getMessage().contains("SELECT"));
    }

    @Test
    void rejectsSemicolon() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> SqlSecurityValidator.validateReadOnlySql("SELECT 1; SELECT 2"));
        assertTrue(ex.getMessage().contains("分号"));
    }

    @Test
    void rejectsForbiddenKeywordInSelect() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> SqlSecurityValidator.validateReadOnlySql("SELECT * FROM t; INSERT INTO u VALUES(1)"));
        assertTrue(ex.getMessage().contains("分号") || ex.getMessage().contains("禁止"));
    }
}
