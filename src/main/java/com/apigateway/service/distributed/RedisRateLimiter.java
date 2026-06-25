package com.apigateway.service.distributed;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class RedisRateLimiter implements RateLimiterPort {

    private static final long WINDOW_MS = 1000L;

    private static final String LUA = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
            local count = redis.call('ZCARD', key)
            if count >= limit then return 0 end
            redis.call('ZADD', key, now, ARGV[4])
            redis.call('PEXPIRE', key, window + 1000)
            return 1
            """;

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA, Long.class);

    @Override
    public boolean tryAcquire(String key, int limit) {
        if (limit <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        String redisKey = "gw:rl:" + key;
        Long allowed = redis.execute(script, List.of(redisKey),
                String.valueOf(now), String.valueOf(WINDOW_MS), String.valueOf(limit),
                now + ":" + UUID.randomUUID());
        return allowed != null && allowed == 1L;
    }
}
