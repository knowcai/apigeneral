package com.apigateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ApiDefinitionRequest {

    @NotBlank
    private String apiCode;

    @NotBlank
    private String name;

    @NotNull
    private Long themeId;

    /** 由服务端根据 themeId 填充，用于 URL 路径 */
    private String theme;

    private String description;
}
