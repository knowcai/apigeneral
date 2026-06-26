package com.apigateway.service.distributed;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryRateLimiterTest {

    private final InMemoryRateLimiter limiter = new InMemoryRateLimiter();

    @Test
    void allowsUpToLimitWithinWindow() {
        assertTrue(limiter.tryAcquire("test-key", 2));
        assertTrue(limiter.tryAcquire("test-key", 2));
        assertFalse(limiter.tryAcquire("test-key", 2));
    }

    @Test
    void separateKeysAreIndependent() {
        assertTrue(limiter.tryAcquire("key-a", 1));
        assertFalse(limiter.tryAcquire("key-a", 1));
        assertTrue(limiter.tryAcquire("key-b", 1));
    }

    @Test
    void zeroOrNegativeLimitAlwaysAllows() {
        assertTrue(limiter.tryAcquire("free", 0));
        assertTrue(limiter.tryAcquire("free", -1));
    }
}
