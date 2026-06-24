package com.apigateway.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ConsumerResponse {

    private Long id;
    private String name;
    private String department;
    private String keyPrefix;
    private String status;
    private List<Long> apiIds;
    private LocalDateTime createdAt;
}
