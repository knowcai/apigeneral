package com.apigateway.service;

import com.apigateway.dto.GatewayPolicyRequest;
import com.apigateway.entity.GatewayPolicy;
import com.apigateway.entity.UserRole;
import com.apigateway.support.TestAuth;
import com.apigateway.support.TestFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class CircuitBreakerServiceIntegrationTest {

    @Autowired
    private CircuitBreakerService circuitBreakerService;
    @Autowired
    private GatewayPolicyService gatewayPolicyService;
    @Autowired
    private TestFixtures fixtures;

    private GatewayPolicy policy;

    @BeforeEach
    void setUp() {
        TestAuth.login(fixtures.requireSuperAdmin().getId(), "testadmin", UserRole.SUPER_ADMIN);
        GatewayPolicyRequest req = new GatewayPolicyRequest();
        req.setGlobalQpsEnabled(false);
        req.setIpQpsEnabled(false);
        req.setApiQpsEnabled(false);
        req.setCircuitEnabled(true);
        req.setCircuitMinCalls(2);
        req.setCircuitFailureRate(50);
        req.setCircuitWaitSec(60);
        req.setRetryEnabled(false);
        policy = gatewayPolicyService.update(req);
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    @Test
    void opensAfterFailureThreshold() {
        String apiCode = "circuit-open-api";
        assertTrue(circuitBreakerService.allowRequest(apiCode, policy));
        circuitBreakerService.recordFailure(apiCode, policy);
        circuitBreakerService.recordFailure(apiCode, policy);
        assertFalse(circuitBreakerService.allowRequest(apiCode, policy));
        assertEquals("OPEN", circuitBreakerService.describeState(apiCode).get("status"));
    }

    @Test
    void successKeepsCircuitClosed() {
        String apiCode = "circuit-success-api";
        circuitBreakerService.recordSuccess(apiCode);
        circuitBreakerService.recordSuccess(apiCode);
        assertTrue(circuitBreakerService.allowRequest(apiCode, policy));
        assertEquals("CLOSED", circuitBreakerService.describeState(apiCode).get("status"));
    }
}
