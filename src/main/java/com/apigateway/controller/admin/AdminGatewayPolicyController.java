package com.apigateway.controller.admin;

import com.apigateway.dto.ApiResponse;
import com.apigateway.dto.GatewayPolicyRequest;
import com.apigateway.entity.GatewayPolicy;
import com.apigateway.service.GatewayPolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/gateway-policy")
@RequiredArgsConstructor
public class AdminGatewayPolicyController {

    private final GatewayPolicyService gatewayPolicyService;

    @GetMapping
    public ApiResponse<GatewayPolicy> get() {
        return ApiResponse.ok(gatewayPolicyService.getForAdmin());
    }

    @PutMapping
    public ApiResponse<GatewayPolicy> update(@Valid @RequestBody GatewayPolicyRequest req) {
        return ApiResponse.ok(gatewayPolicyService.update(req));
    }
}
