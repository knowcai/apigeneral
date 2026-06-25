package com.apigateway.controller;

import com.apigateway.dto.QueryResult;
import com.apigateway.service.DynamicApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Tag(name = "Data API", description = "对外动态 SQL 数据接口，需 API Key")
@RestController
@RequestMapping("/api/data")
@RequiredArgsConstructor
public class DynamicApiController {

    private final DynamicApiService dynamicApiService;

    @Operation(
            summary = "分页查询（GET）",
            description = "SQL 模板参数通过 query 传入。page、pageSize 必填。",
            security = @SecurityRequirement(name = "ApiKeyAuth"))
    @GetMapping("/v{version}/{theme}/{apiCode}")
    public QueryResult queryGet(
            @Parameter(description = "版本号") @PathVariable int version,
            @Parameter(description = "主题编码") @PathVariable String theme,
            @Parameter(description = "API 编码") @PathVariable String apiCode,
            @RequestParam Map<String, String> allParams,
            @Parameter(description = "页码，从 1 开始") @RequestParam(required = false) Integer page,
            @Parameter(description = "每页条数") @RequestParam(required = false) Integer pageSize,
            HttpServletRequest request) {
        Map<String, Object> params = allParams.entrySet().stream()
                .filter(e -> !Set.of("page", "pageSize").contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return dynamicApiService.invoke(theme, apiCode, version, params, page, pageSize, request);
    }

    @Operation(
            summary = "分页查询（POST）",
            description = "SQL 模板参数通过 JSON body 传入；page、pageSize 为 query 参数。",
            security = @SecurityRequirement(name = "ApiKeyAuth"))
    @PostMapping("/v{version}/{theme}/{apiCode}")
    public QueryResult queryPost(
            @PathVariable int version,
            @PathVariable String theme,
            @PathVariable String apiCode,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            HttpServletRequest request) {
        return dynamicApiService.invoke(theme, apiCode, version, body != null ? body : Map.of(),
                page, pageSize, request);
    }
}
