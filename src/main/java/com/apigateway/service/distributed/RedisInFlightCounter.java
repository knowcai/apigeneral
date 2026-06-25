package com.apigateway.service.distributed;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;

@RequiredArgsConstructor
public class RedisInFlightCounter implements InFlightCounterPort {

    private final StringRedisTemplate redis;

    @Override
    public void begin(String apiCode) {
        redis.opsForValue().increment(key(apiCode));
    }

    @Override
    public void end(String apiCode) {
        Long val = redis.opsForValue().decrement(key(apiCode));
        if (val != null && val < 0) {
            redis.opsForValue().set(key(apiCode), "0");
        }
    }

    @Override
    public boolean hasInFlight(String apiCode) {
        return count(apiCode) > 0;
    }

    @Override
    public int count(String apiCode) {
        String val = redis.opsForValue().get(key(apiCode));
        if (val == null) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(val));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String key(String apiCode) {
        return "gw:inflight:" + apiCode;
    }
}
