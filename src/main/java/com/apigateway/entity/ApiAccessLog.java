package com.apigateway.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "api_access_log")
public class ApiAccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, length = 36)
    private String requestId;

    @Column(name = "api_code", length = 100)
    private String apiCode;

    @Column(name = "api_version")
    private Integer apiVersion;

    @Column(name = "consumer_id")
    private Long consumerId;

    @Column(name = "consumer_name", length = 100)
    private String consumerName;

    @Column(name = "client_ip", length = 50)
    private String clientIp;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_params", columnDefinition = "jsonb")
    private Map<String, Object> requestParams;

    @Column(name = "response_mode", length = 20)
    private String responseMode;

    @Column(name = "response_rows")
    private Long responseRows = 0L;

    @Column(name = "response_bytes")
    private Long responseBytes = 0L;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(length = 20)
    private String status;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "auth_mode", length = 20)
    private String authMode;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
