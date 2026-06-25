package com.apigateway.service.distributed;

public interface CircuitBreakerStorePort {

    CircuitBreakerSnapshot load(String apiCode);

    void save(String apiCode, CircuitBreakerSnapshot snapshot);
}
