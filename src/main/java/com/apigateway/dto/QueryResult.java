package com.apigateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryResult {

    private List<Map<String, Object>> rows;
    private long total;
    private int page;
    private int pageSize;
    private boolean hasMore;
}
