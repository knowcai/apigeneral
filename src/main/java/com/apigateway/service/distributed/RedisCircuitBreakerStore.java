package com.apigateway.service.distributed;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

@RequiredArgsConstructor
public class RedisCircuitBreakerStore implements CircuitBreakerStorePort {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final StringRedisTemplate redis;

    @Override
    public CircuitBreakerSnapshot load(String apiCode) {
        String json = redis.opsForValue().get(key(apiCode));
        if (json == null || json.isBlank()) {
            return new CircuitBreakerSnapshot();
        }
        try {
            Map<String, Object> map = MAPPER.readValue(json, new TypeReference<>() {});
            CircuitBreakerSnapshot snap = new CircuitBreakerSnapshot();
            snap.setStatus(CircuitBreakerSnapshot.Status.valueOf(String.valueOf(map.get("status"))));
            snap.setOpenUntilMs(((Number) map.getOrDefault("openUntilMs", 0L)).longValue());
            Deque<CircuitBreakerSnapshot.CallRecord> calls = new ArrayDeque<>();
            Object rawCalls = map.get("recentCalls");
            if (rawCalls instanceof Iterable<?> iterable) {
                for (Object item : iterable) {
                    if (item instanceof Map<?, ?> call) {
                        long ts = ((Number) call.get("timestampMs")).longValue();
                        boolean success = Boolean.TRUE.equals(call.get("success"));
                        calls.addLast(new CircuitBreakerSnapshot.CallRecord(ts, success));
                    }
                }
            }
            snap.setRecentCalls(calls);
            return snap;
        } catch (Exception e) {
            return new CircuitBreakerSnapshot();
        }
    }

    @Override
    public void save(String apiCode, CircuitBreakerSnapshot snapshot) {
        try {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("status", snapshot.getStatus().name());
            map.put("openUntilMs", snapshot.getOpenUntilMs());
            map.put("recentCalls", snapshot.getRecentCalls().stream()
                    .map(c -> Map.of("timestampMs", c.timestampMs(), "success", c.success()))
                    .toList());
            redis.opsForValue().set(key(apiCode), MAPPER.writeValueAsString(map));
        } catch (Exception ignored) {
            // 熔断状态写入失败不阻断主流程
        }
    }

    private String key(String apiCode) {
        return "gw:circuit:" + apiCode;
    }
}
