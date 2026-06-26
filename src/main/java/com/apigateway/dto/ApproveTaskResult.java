package com.apigateway.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ApproveTaskResult {

    private String apiKey;
    private boolean themeKeyPickupPending;
}
