package com.apigateway.service;

import com.apigateway.dto.GatewayPolicyRequest;
import com.apigateway.entity.GatewayPolicy;
import com.apigateway.repository.GatewayPolicyRepository;
import com.apigateway.security.AuthzService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GatewayPolicyService {

    private final GatewayPolicyRepository repository;
    private final AuthzService authzService;
    private final AuditLogService auditLogService;

    public GatewayPolicy getForAdmin() {
        authzService.requireAuthenticated();
        return get();
    }

    /** 运行时策略读取（限流/熔断），公开数据 API 也会调用，不做登录校验。 */
    public GatewayPolicy get() {
        return repository.findById(1L).orElseGet(this::initDefault);
    }

    @Transactional
    public GatewayPolicy update(GatewayPolicyRequest req) {
        authzService.requireSuperAdmin();
        GatewayPolicy policy = get();
        policy.setGlobalQpsEnabled(req.getGlobalQpsEnabled());
        policy.setGlobalQps(req.getGlobalQps());
        policy.setIpQpsEnabled(req.getIpQpsEnabled());
        policy.setIpQps(req.getIpQps());
        policy.setApiQpsEnabled(req.getApiQpsEnabled());
        policy.setApiQps(req.getApiQps());
        policy.setCircuitEnabled(req.getCircuitEnabled());
        policy.setCircuitFailureRate(req.getCircuitFailureRate());
        policy.setCircuitMinCalls(req.getCircuitMinCalls());
        policy.setCircuitWaitSec(req.getCircuitWaitSec());
        if (req.getCircuitFallback() != null && !req.getCircuitFallback().isBlank()) {
            policy.setCircuitFallback(req.getCircuitFallback());
        }
        policy.setRetryEnabled(req.getRetryEnabled());
        policy.setRetryMaxAttempts(req.getRetryMaxAttempts());
        policy.setRetryIntervalMs(req.getRetryIntervalMs());
        GatewayPolicy saved = repository.save(policy);
        auditLogService.log("UPDATE", "GATEWAY_POLICY", "1", "gateway-policy", saved);
        return saved;
    }

    private GatewayPolicy initDefault() {
        GatewayPolicy policy = new GatewayPolicy();
        policy.setId(1L);
        return repository.save(policy);
    }
}
