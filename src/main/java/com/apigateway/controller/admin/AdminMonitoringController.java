package com.apigateway.controller.admin;

import com.apigateway.dto.ApiResponse;
import com.apigateway.service.MonitoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/monitoring")
@RequiredArgsConstructor
public class AdminMonitoringController {

    private final MonitoringService monitoringService;

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard(
            @RequestParam(defaultValue = "24") int hours) {
        return ApiResponse.ok(monitoringService.dashboard(hours));
    }

    @GetMapping("/runtime")
    public ApiResponse<Map<String, Object>> runtime() {
        return ApiResponse.ok(monitoringService.runtime());
    }
}
