package com.apigateway.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "gateway_policy")
public class GatewayPolicy {

    @Id
    private Long id = 1L;

    @Column(name = "global_qps_enabled", nullable = false)
    private Boolean globalQpsEnabled = true;

    @Column(name = "global_qps", nullable = false)
    private Integer globalQps = 1000;

    @Column(name = "ip_qps_enabled", nullable = false)
    private Boolean ipQpsEnabled = true;

    @Column(name = "ip_qps", nullable = false)
    private Integer ipQps = 100;

    @Column(name = "api_qps_enabled", nullable = false)
    private Boolean apiQpsEnabled = true;

    @Column(name = "api_qps", nullable = false)
    private Integer apiQps = 50;

    @Column(name = "circuit_enabled", nullable = false)
    private Boolean circuitEnabled = true;

    @Column(name = "circuit_failure_rate", nullable = false)
    private Integer circuitFailureRate = 50;

    @Column(name = "circuit_min_calls", nullable = false)
    private Integer circuitMinCalls = 20;

    @Column(name = "circuit_wait_sec", nullable = false)
    private Integer circuitWaitSec = 30;

    @Column(name = "circuit_fallback", nullable = false, columnDefinition = "TEXT")
    private String circuitFallback = "{\"code\":503,\"message\":\"该 API 已熔断，请稍后重试\",\"data\":null}";

    @Column(name = "retry_enabled", nullable = false)
    private Boolean retryEnabled = true;

    @Column(name = "retry_max_attempts", nullable = false)
    private Integer retryMaxAttempts = 2;

    @Column(name = "retry_interval_ms", nullable = false)
    private Integer retryIntervalMs = 500;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    @PrePersist
    public void touch() {
        updatedAt = LocalDateTime.now();
    }
}
