package com.apigateway.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ApiDefinitionRequest {

    @NotBlank
    private String apiCode;

    @NotBlank
    private String name;

    private String theme;
    private String description;
    private String createdBy;
    private String updatedBy;
}
