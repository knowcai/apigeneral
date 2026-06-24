package com.apigateway.dto;

import com.apigateway.entity.DatasourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class DatasourceRequest {

    @NotBlank
    private String name;

    @NotNull
    private DatasourceType type;

    @NotBlank
    private String host;

    @NotNull
    private Integer port;

    @NotBlank
    private String databaseName;

    private String username;
    private String password;
    private Map<String, Object> defaultParams;
    private String env = "dev";
    private Boolean readonly = true;
    private String status = "ACTIVE";
    private String description;

    @NotNull
    private Long themeId;
}
