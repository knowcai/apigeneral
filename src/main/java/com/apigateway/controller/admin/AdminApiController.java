package com.apigateway.controller.admin;

import com.apigateway.dto.ApiDefinitionRequest;
import com.apigateway.dto.ApiResponse;
import com.apigateway.dto.ApiVersionRequest;
import com.apigateway.dto.QueryResult;
import com.apigateway.entity.ApiDefinition;
import com.apigateway.entity.ApiVersion;
import com.apigateway.service.ApiManagementService;
import com.apigateway.service.ApiOpenApiExportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/apis")
@RequiredArgsConstructor
public class AdminApiController {

    private final ApiManagementService apiManagementService;
    private final ApiOpenApiExportService openApiExportService;

    @GetMapping
    public ApiResponse<List<ApiDefinition>> list() {
        return ApiResponse.ok(apiManagementService.listDefinitions());
    }

    @GetMapping("/{id}")
    public ApiResponse<ApiDefinition> get(@PathVariable Long id) {
        return ApiResponse.ok(apiManagementService.getDefinition(id));
    }

    @PostMapping
    public ApiResponse<ApiDefinition> create(@Valid @RequestBody ApiDefinitionRequest req) {
        return ApiResponse.ok(apiManagementService.createDefinition(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<ApiDefinition> update(@PathVariable Long id, @Valid @RequestBody ApiDefinitionRequest req) {
        return ApiResponse.ok(apiManagementService.updateDefinition(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        apiManagementService.deleteDefinition(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}/versions")
    public ApiResponse<List<ApiVersion>> versions(@PathVariable Long id) {
        return ApiResponse.ok(apiManagementService.listVersions(id));
    }

    @PostMapping("/{id}/versions")
    public ApiResponse<ApiVersion> createVersion(@PathVariable Long id, @Valid @RequestBody ApiVersionRequest req) {
        return ApiResponse.ok(apiManagementService.createVersion(id, req));
    }

    @PutMapping("/versions/{versionId}")
    public ApiResponse<ApiVersion> updateVersion(@PathVariable Long versionId, @Valid @RequestBody ApiVersionRequest req) {
        return ApiResponse.ok(apiManagementService.updateVersion(versionId, req));
    }

    @PostMapping("/versions/{versionId}/publish")
    public ApiResponse<ApiVersion> publish(@PathVariable Long versionId,
                                           @RequestParam(defaultValue = "admin") String operator) {
        return ApiResponse.ok(apiManagementService.publish(versionId, operator));
    }

    @PostMapping("/versions/{versionId}/suspend")
    public ApiResponse<ApiVersion> suspend(@PathVariable Long versionId) {
        return ApiResponse.ok(apiManagementService.suspend(versionId));
    }

    @PostMapping("/versions/{versionId}/resume")
    public ApiResponse<ApiVersion> resume(@PathVariable Long versionId) {
        return ApiResponse.ok(apiManagementService.resume(versionId));
    }

    @PostMapping("/versions/{versionId}/deprecate")
    public ApiResponse<ApiVersion> deprecate(@PathVariable Long versionId,
                                               @RequestParam(defaultValue = "admin") String operator) {
        return ApiResponse.ok(apiManagementService.deprecate(versionId, operator));
    }

    @GetMapping("/versions/{versionId}/doc")
    public ApiResponse<Map<String, Object>> doc(@PathVariable Long versionId) {
        return ApiResponse.ok(apiManagementService.buildApiDoc(versionId));
    }

    @GetMapping("/versions/{versionId}/endpoint")
    public ApiResponse<Map<String, Object>> endpoint(@PathVariable Long versionId) {
        ApiVersion version = apiManagementService.getVersion(versionId);
        ApiDefinition def = apiManagementService.getDefinition(version.getApiId());
        return ApiResponse.ok(apiManagementService.buildApiPath(def, version));
    }

    @PostMapping("/versions/{versionId}/test")
    public ApiResponse<QueryResult> testVersion(@PathVariable Long versionId,
                                                @RequestBody(required = false) Map<String, Object> params) {
        return ApiResponse.ok(apiManagementService.testVersion(versionId, params));
    }

    @GetMapping("/versions/{versionId}/openapi")
    public ApiResponse<Map<String, Object>> exportOpenApiByVersion(@PathVariable Long versionId) {
        return ApiResponse.ok(openApiExportService.exportByVersionId(versionId));
    }

    @GetMapping("/by-code/{apiCode}/openapi")
    public ApiResponse<Map<String, Object>> exportOpenApiByCode(@PathVariable String apiCode) {
        return ApiResponse.ok(openApiExportService.exportPublishedByApiCode(apiCode));
    }
}
