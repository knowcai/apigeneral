package com.apigateway.controller.admin;

import com.apigateway.dto.ApiResponse;
import com.apigateway.entity.ApiAccessLog;
import com.apigateway.service.AccessLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/logs")
@RequiredArgsConstructor
public class AdminAccessLogController {

    private final AccessLogService accessLogService;

    @GetMapping
    public ApiResponse<Page<ApiAccessLog>> list(
            @RequestParam(required = false) String apiCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(accessLogService.list(apiCode, PageRequest.of(page, size)));
    }
}
