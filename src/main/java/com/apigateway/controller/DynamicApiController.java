package com.apigateway.controller;

import com.apigateway.dto.QueryResult;
import com.apigateway.service.DynamicApiService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/data")
@RequiredArgsConstructor
public class DynamicApiController {

    private final DynamicApiService dynamicApiService;

    @GetMapping("/v{version}/{theme}/{apiCode}")
    public QueryResult queryGet(@PathVariable int version,
                                @PathVariable String theme,
                                @PathVariable String apiCode,
                                @RequestParam Map<String, String> allParams,
                                @RequestParam(required = false) Integer page,
                                @RequestParam(required = false) Integer pageSize,
                                HttpServletRequest request) {
        Map<String, Object> params = allParams.entrySet().stream()
                .filter(e -> !Set.of("page", "pageSize").contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return dynamicApiService.invoke(theme, apiCode, version, params, page, pageSize, request);
    }

    @PostMapping("/v{version}/{theme}/{apiCode}")
    public QueryResult queryPost(@PathVariable int version,
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
