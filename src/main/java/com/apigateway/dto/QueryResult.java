package com.apigateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Schema(description = "分页查询结果")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryResult {

    @Schema(description = "数据行")
    private List<Map<String, Object>> rows;

    @Schema(description = "符合条件的总记录数", example = "1280")
    private long total;

    @Schema(description = "当前页码", example = "1")
    private int page;

    @Schema(description = "每页条数", example = "20")
    private int pageSize;

    @Schema(description = "是否还有下一页")
    private boolean hasMore;
}
