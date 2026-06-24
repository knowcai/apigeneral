package com.apigateway.controller.admin;

import com.apigateway.dto.ApiResponse;
import com.apigateway.dto.ThemeMembersUpdateRequest;
import com.apigateway.dto.ThemeRequest;
import com.apigateway.dto.ThemeResponse;
import com.apigateway.dto.UserInfo;
import com.apigateway.service.ThemeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/themes")
@RequiredArgsConstructor
public class AdminThemeController {

    private final ThemeService themeService;

    @GetMapping
    public ApiResponse<List<ThemeResponse>> list() {
        return ApiResponse.ok(themeService.listAccessible());
    }

    @GetMapping("/{id}")
    public ApiResponse<ThemeResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(themeService.get(id));
    }

    @PostMapping
    public ApiResponse<ThemeResponse> create(@Valid @RequestBody ThemeRequest req) {
        return ApiResponse.ok(themeService.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<ThemeResponse> update(@PathVariable Long id, @Valid @RequestBody ThemeRequest req) {
        return ApiResponse.ok(themeService.update(id, req));
    }

    @PutMapping("/{id}/members")
    public ApiResponse<ThemeResponse> updateMembers(@PathVariable Long id,
                                                    @RequestBody ThemeMembersUpdateRequest req) {
        return ApiResponse.ok(themeService.updateThemeMembers(id, req));
    }

    @GetMapping("/{id}/regular-users")
    public ApiResponse<List<UserInfo>> regularUsers(@PathVariable Long id) {
        return ApiResponse.ok(themeService.listRegularUsersForTheme(id));
    }
}
