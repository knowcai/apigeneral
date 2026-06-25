package com.apigateway.controller.admin;

import com.apigateway.datasource.DatasourceDriverRegistry;
import com.apigateway.dto.ApiResponse;
import com.apigateway.dto.DatasourceRequest;
import com.apigateway.entity.Datasource;
import com.apigateway.service.DatasourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/datasources")
@RequiredArgsConstructor
public class AdminDatasourceController {

    private final DatasourceService datasourceService;
    private final DatasourceDriverRegistry driverRegistry;

    @GetMapping("/types")
    public ApiResponse<List<Map<String, String>>> supportedTypes() {
        return ApiResponse.ok(driverRegistry.listSupported());
    }

    @GetMapping
    public ApiResponse<List<Datasource>> list() {
        return ApiResponse.ok(datasourceService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<Datasource> get(@PathVariable Long id) {
        return ApiResponse.ok(datasourceService.get(id));
    }

    @GetMapping("/param-template/{type}")
    public ApiResponse<Map<String, Object>> paramTemplate(@PathVariable String type) {
        return ApiResponse.ok(datasourceService.defaultParamTemplate(type));
    }

    @PostMapping
    public ApiResponse<Datasource> create(@Valid @RequestBody DatasourceRequest req) {
        return ApiResponse.ok(datasourceService.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<Datasource> update(@PathVariable Long id, @Valid @RequestBody DatasourceRequest req) {
        return ApiResponse.ok(datasourceService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        datasourceService.delete(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/test")
    public ApiResponse<Boolean> test(@PathVariable Long id) {
        return ApiResponse.ok(datasourceService.test(id));
    }

    @PostMapping("/test")
    public ApiResponse<Boolean> testConnection(@RequestBody DatasourceRequest req,
                                               @RequestParam(required = false) Long id) {
        return ApiResponse.ok(datasourceService.test(req, id));
    }
}
