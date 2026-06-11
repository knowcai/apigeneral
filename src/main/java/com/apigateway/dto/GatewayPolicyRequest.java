package com.apigateway.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class GatewayPolicyRequest {

    private Boolean globalQpsEnabled = true;

    @Min(1)
    @Max(100000)
    private Integer globalQps = 1000;

    private Boolean ipQpsEnabled = true;

    @Min(1)
    @Max(10000)
    private Integer ipQps = 100;

    private Boolean apiQpsEnabled = true;

    @Min(1)
    @Max(10000)
    private Integer apiQps = 50;

    private Boolean circuitEnabled = true;

    @Min(1)
    @Max(100)
    private Integer circuitFailureRate = 50;

    @Min(5)
    @Max(1000)
    private Integer circuitMinCalls = 20;

    @Min(5)
    @Max(600)
    private Integer circuitWaitSec = 30;

    private String circuitFallback;

    private Boolean retryEnabled = true;

    @Min(0)
    @Max(5)
    private Integer retryMaxAttempts = 2;

    @Min(100)
    @Max(10000)
    private Integer retryIntervalMs = 500;
}
