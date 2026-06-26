package com.apigateway.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ThemeApiKeyRequest {

    @NotBlank
    private String name;

    private String department;

    private String status = "ACTIVE";
}
