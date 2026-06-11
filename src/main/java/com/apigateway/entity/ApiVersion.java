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
@Table(name = "api_version")
public class ApiVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "api_id", nullable = false)
    private Long apiId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "datasource_id", nullable = false)
    private Long datasourceId;

    @Column(name = "sql_template", nullable = false, columnDefinition = "TEXT")
    private String sqlTemplate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "param_schema", columnDefinition = "jsonb")
    private Map<String, Object> paramSchema;

    @Enumerated(EnumType.STRING)
    @Column(name = "response_mode", nullable = false, length = 20)
    private ResponseMode responseMode = ResponseMode.PAGE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_config", columnDefinition = "jsonb")
    private Map<String, Object> responseConfig;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PublishStatus status = PublishStatus.DRAFT;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (paramSchema == null) {
            paramSchema = Map.of();
        }
        if (responseConfig == null) {
            responseConfig = Map.of();
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
