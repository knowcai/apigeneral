package com.apigateway.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** 跟踪每个 apiCode 正在处理的动态 API 请求数，用于发布前校验。 */
@Component
public class InFlightRequestTracker {

    private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    public void begin(String apiCode) {
        counters.computeIfAbsent(apiCode, k -> new AtomicInteger()).incrementAndGet();
    }

    public void end(String apiCode) {
        AtomicInteger counter = counters.get(apiCode);
        if (counter != null) {
            counter.decrementAndGet();
        }
    }

    public boolean hasInFlight(String apiCode) {
        AtomicInteger counter = counters.get(apiCode);
        return counter != null && counter.get() > 0;
    }

    public int count(String apiCode) {
        AtomicInteger counter = counters.get(apiCode);
        return counter == null ? 0 : Math.max(0, counter.get());
    }
}
