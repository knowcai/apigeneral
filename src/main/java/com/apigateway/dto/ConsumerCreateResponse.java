package com.apigateway.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConsumerCreateResponse {

    private ConsumerResponse consumer;
    /** 完整 Key，仅创建或轮换时返回一次 */
    private String apiKey;
}
