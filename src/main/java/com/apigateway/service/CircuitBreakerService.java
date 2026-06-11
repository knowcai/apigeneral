package com.apigateway.service;

import com.apigateway.entity.GatewayPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
public class CircuitBreakerService {

    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, CircuitState> states = new ConcurrentHashMap<>();

    public boolean allowRequest(String apiCode, GatewayPolicy policy) {
        if (!Boolean.TRUE.equals(policy.getCircuitEnabled())) {
            return true;
        }
        CircuitState state = states.computeIfAbsent(apiCode, k -> new CircuitState());
        synchronized (state) {
            if (state.status == Status.OPEN) {
                if (System.currentTimeMillis() >= state.openUntilMs) {
                    state.status = Status.HALF_OPEN;
                } else {
                    return false;
                }
            }
            return true;
        }
    }

    public void recordSuccess(String apiCode) {
        CircuitState state = states.get(apiCode);
        if (state == null) {
            return;
        }
        synchronized (state) {
            if (state.status == Status.HALF_OPEN) {
                state.reset();
            } else {
                state.record(true);
            }
        }
    }

    public void recordFailure(String apiCode, GatewayPolicy policy) {
        if (!Boolean.TRUE.equals(policy.getCircuitEnabled())) {
            return;
        }
        CircuitState state = states.computeIfAbsent(apiCode, k -> new CircuitState());
        synchronized (state) {
            if (state.status == Status.HALF_OPEN) {
                open(state, policy);
                return;
            }
            state.record(false);
            if (state.totalCalls() >= policy.getCircuitMinCalls()) {
                int failureRate = (int) (state.failures.get() * 100.0 / state.totalCalls());
                if (failureRate >= policy.getCircuitFailureRate()) {
                    open(state, policy);
                }
            }
        }
    }

    public Object parseFallback(GatewayPolicy policy) {
        try {
            return objectMapper.readValue(policy.getCircuitFallback(), Map.class);
        } catch (Exception e) {
            return Map.of("code", 503, "message", "服务熔断中，请稍后重试", "data", null);
        }
    }

    private void open(CircuitState state, GatewayPolicy policy) {
        state.status = Status.OPEN;
        state.openUntilMs = System.currentTimeMillis() + policy.getCircuitWaitSec() * 1000L;
    }

    private enum Status { CLOSED, OPEN, HALF_OPEN }

    private static class CircuitState {
        private Status status = Status.CLOSED;
        private long openUntilMs;
        private final AtomicInteger successes = new AtomicInteger();
        private final AtomicInteger failures = new AtomicInteger();

        void record(boolean success) {
            if (success) {
                successes.incrementAndGet();
            } else {
                failures.incrementAndGet();
            }
        }

        int totalCalls() {
            return successes.get() + failures.get();
        }

        void reset() {
            status = Status.CLOSED;
            openUntilMs = 0;
            successes.set(0);
            failures.set(0);
        }
    }
}
