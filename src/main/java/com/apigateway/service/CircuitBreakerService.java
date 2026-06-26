package com.apigateway.service;

import com.apigateway.entity.GatewayPolicy;
import com.apigateway.service.distributed.CircuitBreakerSnapshot;
import com.apigateway.service.distributed.CircuitBreakerStorePort;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.LinkedHashMap;

/**
 * 单 API 熔断器：按 {@code apiCode} 独立维护状态，互不影响。
 * 状态存储可切换为 Redis（{@code gateway.redis.enabled=true}）以支持多实例。
 */
@Component
@RequiredArgsConstructor
public class CircuitBreakerService {

    private static final long STATS_WINDOW_MS = 60_000L;

    private final ObjectMapper objectMapper;
    private final CircuitBreakerStorePort store;

    public boolean allowRequest(String apiCode, GatewayPolicy policy) {
        if (!Boolean.TRUE.equals(policy.getCircuitEnabled())) {
            return true;
        }
        synchronized (lockFor(apiCode)) {
            CircuitBreakerSnapshot state = store.load(apiCode);
            if (state.getStatus() == CircuitBreakerSnapshot.Status.OPEN) {
                if (System.currentTimeMillis() >= state.getOpenUntilMs()) {
                    state.setStatus(CircuitBreakerSnapshot.Status.HALF_OPEN);
                    store.save(apiCode, state);
                } else {
                    return false;
                }
            }
            return true;
        }
    }

    public void recordSuccess(String apiCode) {
        synchronized (lockFor(apiCode)) {
            CircuitBreakerSnapshot state = store.load(apiCode);
            if (state.getStatus() == CircuitBreakerSnapshot.Status.HALF_OPEN) {
                reset(state);
                store.save(apiCode, state);
            } else {
                record(state, true);
                store.save(apiCode, state);
            }
        }
    }

    public void recordFailure(String apiCode, GatewayPolicy policy) {
        if (!Boolean.TRUE.equals(policy.getCircuitEnabled())) {
            return;
        }
        synchronized (lockFor(apiCode)) {
            CircuitBreakerSnapshot state = store.load(apiCode);
            if (state.getStatus() == CircuitBreakerSnapshot.Status.HALF_OPEN) {
                open(state, policy);
                store.save(apiCode, state);
                return;
            }
            record(state, false);
            int total = state.getRecentCalls().size();
            if (total >= policy.getCircuitMinCalls()) {
                int failures = failureCount(state);
                int failureRate = (int) (failures * 100.0 / total);
                if (failureRate >= policy.getCircuitFailureRate()) {
                    open(state, policy);
                }
            }
            store.save(apiCode, state);
        }
    }

    public Object parseFallback(GatewayPolicy policy) {
        try {
            return objectMapper.readValue(policy.getCircuitFallback(), Map.class);
        } catch (Exception e) {
            return Map.of("code", 503, "message", "该 API 已熔断，请稍后重试", "data", null);
        }
    }

    /** 当前熔断状态快照（供监控大盘展示）。 */
    public Map<String, Object> describeState(String apiCode) {
        synchronized (lockFor(apiCode)) {
            CircuitBreakerSnapshot state = store.load(apiCode);
            long now = System.currentTimeMillis();
            pruneExpired(state, now);
            int total = state.getRecentCalls().size();
            int failures = failureCount(state);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("apiCode", apiCode);
            m.put("status", state.getStatus().name());
            m.put("windowCalls", total);
            m.put("windowFailures", failures);
            m.put("failureRatePercent", total > 0 ? Math.round(failures * 1000.0 / total) / 10.0 : 0);
            if (state.getStatus() == CircuitBreakerSnapshot.Status.OPEN && state.getOpenUntilMs() > 0) {
                m.put("openUntil", java.time.Instant.ofEpochMilli(state.getOpenUntilMs()).toString());
            }
            return m;
        }
    }

    private void open(CircuitBreakerSnapshot state, GatewayPolicy policy) {
        state.setStatus(CircuitBreakerSnapshot.Status.OPEN);
        state.setOpenUntilMs(System.currentTimeMillis() + policy.getCircuitWaitSec() * 1000L);
    }

    private void record(CircuitBreakerSnapshot state, boolean success) {
        long now = System.currentTimeMillis();
        pruneExpired(state, now);
        state.getRecentCalls().addLast(new CircuitBreakerSnapshot.CallRecord(now, success));
    }

    private void pruneExpired(CircuitBreakerSnapshot state, long now) {
        while (!state.getRecentCalls().isEmpty()
                && now - state.getRecentCalls().peekFirst().timestampMs() >= STATS_WINDOW_MS) {
            state.getRecentCalls().pollFirst();
        }
    }

    private int failureCount(CircuitBreakerSnapshot state) {
        int failures = 0;
        for (CircuitBreakerSnapshot.CallRecord record : state.getRecentCalls()) {
            if (!record.success()) {
                failures++;
            }
        }
        return failures;
    }

    private void reset(CircuitBreakerSnapshot state) {
        state.setStatus(CircuitBreakerSnapshot.Status.CLOSED);
        state.setOpenUntilMs(0);
        state.getRecentCalls().clear();
    }

    private Object lockFor(String apiCode) {
        return ("circuit:" + apiCode).intern();
    }
}
