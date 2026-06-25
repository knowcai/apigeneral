package com.apigateway.service;

import com.apigateway.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ParamSchemaValidatorTest {

    @Test
    void rejectsMissingRequiredParam() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                ParamSchemaValidator.validate("SELECT * FROM t WHERE id = :id", Map.of(), Map.of()));
        assertTrue(ex.getMessage().contains("id"));
    }

    @Test
    void validatesIntegerType() {
        Map<String, Object> schema = Map.of("id", Map.of("type", "integer", "required", true));
        assertDoesNotThrow(() -> ParamSchemaValidator.validate(
                "SELECT * FROM t WHERE id = :id", schema, Map.of("id", 1)));
        BusinessException ex = assertThrows(BusinessException.class, () ->
                ParamSchemaValidator.validate(
                        "SELECT * FROM t WHERE id = :id", schema, Map.of("id", "abc")));
        assertTrue(ex.getMessage().contains("整数"));
    }
}
