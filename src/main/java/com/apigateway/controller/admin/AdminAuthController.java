package com.apigateway.controller.admin;

import com.apigateway.dto.ApiResponse;
import com.apigateway.dto.LoginRequest;
import com.apigateway.dto.UserInfo;
import com.apigateway.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Admin Auth", description = "管理端认证")
@RestController
@RequestMapping("/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final UserService userService;

    @Operation(summary = "登录", description = "返回 JWT token，用于管理端 API")
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest req) {
        return ApiResponse.ok(userService.login(req));
    }

    @GetMapping("/me")
    public ApiResponse<UserInfo> me() {
        return ApiResponse.ok(userService.me());
    }
}
