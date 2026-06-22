package com.apigateway.service;

import com.apigateway.entity.GatewayPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单 API 熔断器：按 {@code apiCode} 独立维护状态，互不影响。
 * <p>
 * 失败率在「最近 1 分钟」滚动窗口内计算，仅统计已进入 SQL 执行的成功/失败结果；
 * 限流、参数校验、熔断拦截等不计入窗口。
 */
@Component
@RequiredArgsConstructor
public class CircuitBreakerService {

    /** 失败率滚动统计窗口：最近 1 分钟内的 SQL 执行结果。 */
    private static final long STATS_WINDOW_MS = 60_000L;

    private final ObjectMapper objectMapper;
    /** key = apiCode，每个 API 一份独立的熔断状态与统计窗口。 */
    private final ConcurrentHashMap<String, CircuitState> states = new ConcurrentHashMap<>();

    /** 判断该 API 当前是否允许执行 SQL（CLOSED / HALF_OPEN 放行，OPEN 且未到恢复时间则拒绝）。 */
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

    /** SQL 执行成功后记录；半开试探成功则关闭熔断并清空该 API 的统计窗口。 */
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

    /**
     * SQL 执行失败后记录，并在滚动窗口内重新计算该 API 的失败率；
     * 达到阈值则将该 API 置为 OPEN。半开试探失败则直接再次熔断。
     */
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
            int total = state.totalCalls();
            if (total >= policy.getCircuitMinCalls()) {
                int failures = state.failureCount();
                int failureRate = (int) (failures * 100.0 / total);
                if (failureRate >= policy.getCircuitFailureRate()) {
                    open(state, policy);
                }
            }
        }
    }

    /** 该 API 熔断时返回给客户端的 JSON 响应体。 */
    public Object parseFallback(GatewayPolicy policy) {
        try {
            return objectMapper.readValue(policy.getCircuitFallback(), Map.class);
        } catch (Exception e) {
            return Map.of("code", 503, "message", "该 API 已熔断，请稍后重试", "data", null);
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
        private final Deque<CallRecord> recentCalls = new ArrayDeque<>();

        private record CallRecord(long timestampMs, boolean success) {}

        void record(boolean success) {
            long now = System.currentTimeMillis();
            pruneExpired(now);
            recentCalls.addLast(new CallRecord(now, success));
        }

        void pruneExpired(long now) {
            while (!recentCalls.isEmpty() && now - recentCalls.peekFirst().timestampMs >= STATS_WINDOW_MS) {
                recentCalls.pollFirst();
            }
        }

        int totalCalls() {
            return recentCalls.size();
        }

        int failureCount() {
            int failures = 0;
            for (CallRecord record : recentCalls) {
                if (!record.success()) {
                    failures++;
                }
            }
            return failures;
        }

        void reset() {
            status = Status.CLOSED;
            openUntilMs = 0;
            recentCalls.clear();
        }
    }
}

