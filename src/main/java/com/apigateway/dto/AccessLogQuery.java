package com.apigateway.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AccessLogQuery {

    private String apiCode;
    private String status;
    private String consumerName;
    private LocalDateTime from;
    private LocalDateTime to;
    private int page = 0;
    private int size = 20;
}
