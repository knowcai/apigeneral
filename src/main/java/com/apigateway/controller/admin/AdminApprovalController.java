package com.apigateway.controller.admin;

import com.apigateway.dto.ApiResponse;
import com.apigateway.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/approvals")
@RequiredArgsConstructor
public class AdminApprovalController {

    private final ApprovalService approvalService;

    @GetMapping("/my-tasks")
    public ApiResponse<List<Map<String, Object>>> myTasks() {
        return ApiResponse.ok(approvalService.listMyPendingTasks());
    }

    @GetMapping("/pending")
    public ApiResponse<List<Map<String, Object>>> pending() {
        return ApiResponse.ok(approvalService.listPendingRequests());
    }

    @PostMapping("/tasks/{taskId}/approve")
    public ApiResponse<Void> approve(@PathVariable Long taskId, @RequestBody(required = false) Map<String, String> body) {
        approvalService.approveTask(taskId, true, body != null ? body.get("comment") : null);
        return ApiResponse.ok(null);
    }

    @PostMapping("/tasks/{taskId}/reject")
    public ApiResponse<Void> reject(@PathVariable Long taskId, @RequestBody(required = false) Map<String, String> body) {
        approvalService.approveTask(taskId, false, body != null ? body.get("comment") : null);
        return ApiResponse.ok(null);
    }
}
