package com.apigateway.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Builder
public class AccessLogView {

    private Long id;
    private String requestId;
    private String apiCode;
    private Integer apiVersion;
    private Long consumerId;
    private String consumerName;
    private String clientIp;
    private Map<String, Object> requestParams;
    private String responseMode;
    private Long responseRows;
    private Long responseBytes;
    private Long durationMs;
    private String status;
    private String errorMessage;
    private String authMode;
    private LocalDateTime createdAt;
}
