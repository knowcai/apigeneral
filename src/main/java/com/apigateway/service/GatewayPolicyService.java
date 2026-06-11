package com.apigateway.service;

import com.apigateway.dto.GatewayPolicyRequest;
import com.apigateway.entity.GatewayPolicy;
import com.apigateway.repository.GatewayPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GatewayPolicyService {

    private final GatewayPolicyRepository repository;

    public GatewayPolicy get() {
        return repository.findById(1L).orElseGet(this::initDefault);
    }

    @Transactional
    public GatewayPolicy update(GatewayPolicyRequest req) {
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
        return repository.save(policy);
    }

    private GatewayPolicy initDefault() {
        GatewayPolicy policy = new GatewayPolicy();
        policy.setId(1L);
        return repository.save(policy);
    }
}
