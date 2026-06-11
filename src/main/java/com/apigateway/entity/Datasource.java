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
@Table(name = "datasource")
public class Datasource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DatasourceType type;

    @Column(nullable = false)
    private String host;

    @Column(nullable = false)
    private Integer port;

    @Column(name = "database_name", nullable = false, length = 100)
    private String databaseName;

    private String username;

    private String password;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "default_params", columnDefinition = "jsonb")
    private Map<String, Object> defaultParams;

    @Column(length = 20)
    private String env = "dev";

    private Boolean readonly = true;

    @Column(length = 20)
    private String status = "ACTIVE";

    private String description;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (defaultParams == null) {
            defaultParams = Map.of();
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
