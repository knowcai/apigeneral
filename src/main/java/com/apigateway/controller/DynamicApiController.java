package com.apigateway.controller;

import com.apigateway.dto.ApiResponse;
import com.apigateway.service.DynamicApiService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/data")
@RequiredArgsConstructor
public class DynamicApiController {

    private final DynamicApiService dynamicApiService;

    @GetMapping("/v{version}/{theme}/{apiCode}")
    public Object queryGet(@PathVariable int version,
                           @PathVariable String theme,
                           @PathVariable String apiCode,
                           @RequestParam Map<String, String> allParams,
                           @RequestParam(defaultValue = "1") int page,
                           @RequestParam(defaultValue = "0") int pageSize,
                           @RequestParam(defaultValue = "0") int chunkIndex,
                           HttpServletRequest request) {
        Map<String, Object> params = allParams.entrySet().stream()
                .filter(e -> !Set.of("page", "pageSize", "chunkIndex").contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return dynamicApiService.invoke(theme, apiCode, version, params, page, pageSize, chunkIndex, request);
    }

    @PostMapping("/v{version}/{theme}/{apiCode}")
    public Object queryPost(@PathVariable int version,
                            @PathVariable String theme,
                            @PathVariable String apiCode,
                            @RequestBody(required = false) Map<String, Object> body,
                            @RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "0") int pageSize,
                            @RequestParam(defaultValue = "0") int chunkIndex,
                            HttpServletRequest request) {
        return dynamicApiService.invoke(theme, apiCode, version, body != null ? body : Map.of(),
                page, pageSize, chunkIndex, request);
    }

    @GetMapping(value = "/v{version}/{theme}/{apiCode}/stream", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public StreamingResponseBody stream(@PathVariable int version,
                                        @PathVariable String theme,
                                        @PathVariable String apiCode,
                                        @RequestParam Map<String, String> allParams,
                                        HttpServletRequest request) {
        Object result = dynamicApiService.invoke(theme, apiCode, version, Map.copyOf(allParams),
                1, 0, 0, request);
        return (StreamingResponseBody) result;
    }
}
