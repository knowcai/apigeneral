package com.apigateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class ConsumerRequest {

    @NotBlank
    private String name;

    private String department;

    private String status = "ACTIVE";

    @NotEmpty
    private List<Long> apiIds;
}
