package com.apigateway.dto;

import com.apigateway.entity.Datasource;
import com.apigateway.entity.DatasourceType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class DatasourceResponse {

    private Long id;
    private String name;
    private DatasourceType type;
    private String host;
    private Integer port;
    private String databaseName;
    private String username;
    private boolean passwordConfigured;
    private Map<String, Object> defaultParams;
    private String env;
    private Boolean readonly;
    private String status;
    private String description;
    private Long themeId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static DatasourceResponse from(Datasource ds) {
        return DatasourceResponse.builder()
                .id(ds.getId())
                .name(ds.getName())
                .type(ds.getType())
                .host(ds.getHost())
                .port(ds.getPort())
                .databaseName(ds.getDatabaseName())
                .username(ds.getUsername())
                .passwordConfigured(ds.getPassword() != null && !ds.getPassword().isBlank())
                .defaultParams(ds.getDefaultParams())
                .env(ds.getEnv())
                .readonly(ds.getReadonly())
                .status(ds.getStatus())
                .description(ds.getDescription())
                .themeId(ds.getThemeId())
                .createdAt(ds.getCreatedAt())
                .updatedAt(ds.getUpdatedAt())
                .build();
    }
}
