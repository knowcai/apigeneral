package com.apigateway.controller.admin;

import com.apigateway.dto.ApproveTaskResult;
import com.apigateway.dto.ApiResponse;
import com.apigateway.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/approvals")
@RequiredArgsConstructor
public class AdminApprovalController {

    private final ApprovalService approvalService;

    @GetMapping("/my-tasks/count")
    public ApiResponse<Map<String, Long>> myTaskCount() {
        return ApiResponse.ok(Map.of("count", approvalService.countMyPendingTasks()));
    }

    @GetMapping("/my-tasks")
    public ApiResponse<List<Map<String, Object>>> myTasks() {
        return ApiResponse.ok(approvalService.listMyPendingTasks());
    }

    @GetMapping("/pending")
    public ApiResponse<List<Map<String, Object>>> pending() {
        return ApiResponse.ok(approvalService.listPendingRequests());
    }

    @GetMapping("/history")
    public ApiResponse<List<Map<String, Object>>> history(
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(approvalService.listHistory(limit));
    }

    @PostMapping("/tasks/{taskId}/approve")
    public ApiResponse<Map<String, Object>> approve(@PathVariable Long taskId,
                                                    @RequestBody(required = false) Map<String, String> body) {
        ApproveTaskResult result = approvalService.approveTask(taskId, true, body != null ? body.get("comment") : null);
        Map<String, Object> data = new LinkedHashMap<>();
        if (result.getApiKey() != null) {
            data.put("apiKey", result.getApiKey());
        }
        if (result.isThemeKeyPickupPending()) {
            data.put("themeKeyPickupPending", true);
        }
        return ApiResponse.ok(data);
    }

    @PostMapping("/tasks/{taskId}/reject")
    public ApiResponse<Void> reject(@PathVariable Long taskId, @RequestBody(required = false) Map<String, String> body) {
        approvalService.approveTask(taskId, false, body != null ? body.get("comment") : null);
        return ApiResponse.ok(null);
    }

    @PostMapping("/requests/{requestId}/withdraw")
    public ApiResponse<Void> withdraw(@PathVariable Long requestId) {
        approvalService.withdrawRequest(requestId);
        return ApiResponse.ok(null);
    }
}
