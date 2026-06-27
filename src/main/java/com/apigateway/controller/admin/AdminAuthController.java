package com.apigateway.controller.admin;

import com.apigateway.config.GatewaySecurityProperties;
import com.apigateway.dto.ApiResponse;
import com.apigateway.dto.LoginRequest;
import com.apigateway.dto.UserInfo;
import com.apigateway.service.JwtService;
import com.apigateway.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
    private final JwtService jwtService;
    private final GatewaySecurityProperties securityProperties;

    @Operation(summary = "登录", description = "返回 JWT token，并设置 HttpOnly Cookie")
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest req,
                                                  HttpServletRequest request,
                                                  HttpServletResponse response) {
        Map<String, Object> result = userService.login(req, resolveClientIp(request));
        String token = (String) result.get("token");
        if (token != null) {
            attachJwtCookie(response, token);
        }
        return ApiResponse.ok(result);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletResponse response) {
        clearJwtCookie(response);
        return ApiResponse.ok(null);
    }

    @GetMapping("/me")
    public ApiResponse<UserInfo> me() {
        return ApiResponse.ok(userService.me());
    }

    private void attachJwtCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie(securityProperties.getJwtCookieName(), token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(jwtService.expirationSeconds());
        cookie.setSecure(securityProperties.isJwtCookieSecure());
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }

    private void clearJwtCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(securityProperties.getJwtCookieName(), "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setSecure(securityProperties.isJwtCookieSecure());
        response.addCookie(cookie);
    }

    private String resolveClientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
