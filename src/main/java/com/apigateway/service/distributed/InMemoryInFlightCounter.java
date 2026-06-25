package com.apigateway.service.distributed;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@ConditionalOnProperty(name = "gateway.redis.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryInFlightCounter implements InFlightCounterPort {

    private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    @Override
    public void begin(String apiCode) {
        counters.computeIfAbsent(apiCode, k -> new AtomicInteger()).incrementAndGet();
    }

    @Override
    public void end(String apiCode) {
        AtomicInteger counter = counters.get(apiCode);
        if (counter != null) {
            counter.decrementAndGet();
        }
    }

    @Override
    public boolean hasInFlight(String apiCode) {
        return count(apiCode) > 0;
    }

    @Override
    public int count(String apiCode) {
        AtomicInteger counter = counters.get(apiCode);
        return counter == null ? 0 : Math.max(0, counter.get());
    }
}
