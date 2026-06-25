package com.apigateway.service.distributed;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "gateway.redis.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryCircuitBreakerStore implements CircuitBreakerStorePort {

    private final ConcurrentHashMap<String, CircuitBreakerSnapshot> states = new ConcurrentHashMap<>();

    @Override
    public CircuitBreakerSnapshot load(String apiCode) {
        return states.computeIfAbsent(apiCode, k -> new CircuitBreakerSnapshot());
    }

    @Override
    public void save(String apiCode, CircuitBreakerSnapshot snapshot) {
        states.put(apiCode, snapshot);
    }
}
