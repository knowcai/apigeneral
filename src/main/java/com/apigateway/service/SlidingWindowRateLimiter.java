package com.apigateway.service;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SlidingWindowRateLimiter {

    private static final long WINDOW_MS = 1000L;

    private final ConcurrentHashMap<String, Deque<Long>> windows = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key, int limit) {
        if (limit <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        Deque<Long> deque = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && now - deque.peekFirst() >= WINDOW_MS) {
                deque.pollFirst();
            }
            if (deque.size() >= limit) {
                return false;
            }
            deque.addLast(now);
            return true;
        }
    }
}
