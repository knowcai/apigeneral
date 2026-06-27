package com.apigateway.controller.admin;

import com.apigateway.dto.ApiResponse;
import com.apigateway.dto.ConsumerCreateResponse;
import com.apigateway.dto.ThemeApiKeyListResponse;
import com.apigateway.dto.ThemeApiKeyRequest;
import com.apigateway.dto.ThemeMembersUpdateRequest;
import com.apigateway.dto.ThemeRequest;
import com.apigateway.dto.ThemeResponse;
import com.apigateway.dto.UserInfo;
import com.apigateway.service.ThemeApiKeyService;
import com.apigateway.service.ThemeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/themes")
@RequiredArgsConstructor
public class AdminThemeController {

    private final ThemeService themeService;
    private final ThemeApiKeyService themeApiKeyService;

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

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        themeService.delete(id);
        return ApiResponse.ok(null);
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

    @GetMapping("/{id}/impact")
    public ApiResponse<Map<String, Object>> impact(@PathVariable Long id) {
        return ApiResponse.ok(themeService.impactStats(id));
    }

    @GetMapping("/{id}/api-keys")
    public ApiResponse<ThemeApiKeyListResponse> listApiKeys(@PathVariable Long id) {
        return ApiResponse.ok(themeApiKeyService.listKeys(id));
    }

    @PostMapping("/{id}/api-keys")
    public ApiResponse<ConsumerCreateResponse> createApiKey(@PathVariable Long id,
                                                            @Valid @RequestBody ThemeApiKeyRequest req) {
        return ApiResponse.ok(themeApiKeyService.create(id, req));
    }

    @DeleteMapping("/{id}/api-keys/{keyId}")
    public ApiResponse<Void> deleteApiKey(@PathVariable Long id, @PathVariable Long keyId) {
        themeApiKeyService.deleteKey(id, keyId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/api-keys/{keyId}/claim")
    public ApiResponse<ConsumerCreateResponse> claimApiKey(@PathVariable Long id, @PathVariable Long keyId) {
        return ApiResponse.ok(themeApiKeyService.claimPickup(id, keyId));
    }
}
