package com.apigateway.pool;

import com.apigateway.config.GatewayPoolProperties;
import com.apigateway.exception.BusinessException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GlobalConnectionBudgetTest {

    @Test
    void blocksWhenExhausted() {
        GatewayPoolProperties props = new GatewayPoolProperties();
        props.setGlobalMaxConnections(1);
        props.setAcquireTimeoutMs(50);
        GlobalConnectionBudget budget = new GlobalConnectionBudget(props, new SimpleMeterRegistry());

        budget.acquire();
        BusinessException ex = assertThrows(BusinessException.class, budget::acquire);
        assertEquals(503, ex.getCode());
        budget.release();
        assertDoesNotThrow(budget::acquire);
        budget.release();
    }
}
