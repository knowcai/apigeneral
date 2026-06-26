package com.apigateway.controller.admin;

import com.apigateway.dto.ApiResponse;
import com.apigateway.dto.ConsumerResponse;
import com.apigateway.service.ConsumerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/consumers")
@RequiredArgsConstructor
public class AdminConsumerController {

    private final ConsumerService consumerService;

    /** 只读：查看全部 Key。创建/编辑/轮换请至「主题管理」。 */
    @GetMapping
    public ApiResponse<List<ConsumerResponse>> list() {
        return ApiResponse.ok(consumerService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<ConsumerResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(consumerService.get(id));
    }

    @GetMapping("/legacy-migration")
    public ApiResponse<Map<String, Object>> legacyMigration(@RequestParam(defaultValue = "168") int hours) {
        return ApiResponse.ok(consumerService.legacyMigrationStats(hours));
    }
}
