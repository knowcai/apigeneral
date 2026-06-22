package com.apigateway.dto;

import com.apigateway.entity.ResponseMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class ApiVersionRequest {

    @NotNull
    private Long datasourceId;

    @NotBlank
    private String sqlTemplate;

    private ResponseMode responseMode = ResponseMode.PAGE;
    private Map<String, Object> responseConfig;
    private String createdBy;
    private String updatedBy;
}
