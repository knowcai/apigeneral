package com.apigateway.service.distributed;

public interface RateLimiterPort {

    boolean tryAcquire(String key, int limit);
}
