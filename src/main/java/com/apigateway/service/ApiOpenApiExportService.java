package com.apigateway.service;

import com.apigateway.entity.ApiDefinition;
import com.apigateway.entity.ApiVersion;
import com.apigateway.entity.PublishStatus;
import com.apigateway.exception.BusinessException;
import com.apigateway.repository.ApiVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.apigateway.util.SqlParamPatterns;

@Service
@RequiredArgsConstructor
public class ApiOpenApiExportService {

    private static final Pattern NAMED_PARAM = SqlParamPatterns.NAMED_PARAM;

    private final ApiManagementService apiManagementService;
    private final ApiVersionRepository versionRepository;

    public Map<String, Object> exportPublishedByApiCode(String apiCode) {
        ApiDefinition def = apiManagementService.getDefinition(
                apiManagementService.getByCode(apiCode).getId());
        ApiVersion version = versionRepository
                .findFirstByApiIdAndStatusOrderByVersionNoDesc(def.getId(), PublishStatus.PUBLISHED)
                .orElseThrow(() -> new BusinessException("API 暂无已发布版本: " + apiCode));
        return buildSpec(def, version);
    }

    public Map<String, Object> exportByVersionId(Long versionId) {
        ApiVersion version = apiManagementService.getVersion(versionId);
        ApiDefinition def = apiManagementService.getDefinition(version.getApiId());
        return buildSpec(def, version);
    }

    private Map<String, Object> buildSpec(ApiDefinition def, ApiVersion version) {
        String theme = def.getTheme() != null ? def.getTheme() : "default";
        String path = "/api/data/v" + version.getVersionNo() + "/" + theme + "/" + def.getApiCode();

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("title", def.getName());
        info.put("description", def.getDescription());
        info.put("version", "v" + version.getVersionNo());

        List<Map<String, Object>> parameters = new ArrayList<>();
        parameters.add(param("page", "query", "integer", true, "页码，从 1 开始"));
        parameters.add(param("pageSize", "query", "integer", true, "每页条数"));
        for (String sqlParam : extractSqlParams(version.getSqlTemplate())) {
            parameters.add(param(sqlParam, "query", schemaType(version, sqlParam), true,
                    "SQL 参数 :" + sqlParam));
        }

        Map<String, Object> getOp = new LinkedHashMap<>();
        getOp.put("summary", def.getName());
        getOp.put("description", "SQL: " + version.getSqlTemplate());
        getOp.put("parameters", parameters);
        getOp.put("security", List.of(Map.of("ApiKeyAuth", List.of())));
        getOp.put("responses", Map.of(
                "200", Map.of("description", "成功", "content", Map.of(
                        "application/json", Map.of("schema", Map.of("$ref", "#/components/schemas/QueryResult")))),
                "401", errorResponse("缺少或无效 API Key"),
                "403", errorResponse("无 API 授权"),
                "429", errorResponse("限流")));

        Map<String, Object> paths = Map.of(path, Map.of("get", getOp, "post", mergePostBody(getOp)));

        Map<String, Object> components = new LinkedHashMap<>();
        components.put("securitySchemes", Map.of(
                "ApiKeyAuth", Map.of(
                        "type", "apiKey",
                        "in", "header",
                        "name", "X-Api-Key")));
        components.put("schemas", Map.of(
                "QueryResult", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "rows", Map.of("type", "array", "items", Map.of("type", "object")),
                                "total", Map.of("type", "integer"),
                                "page", Map.of("type", "integer"),
                                "pageSize", Map.of("type", "integer"),
                                "hasMore", Map.of("type", "boolean")))));

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("openapi", "3.0.3");
        spec.put("info", info);
        spec.put("paths", paths);
        spec.put("components", components);
        return spec;
    }

    private Map<String, Object> mergePostBody(Map<String, Object> getOp) {
        Map<String, Object> post = new LinkedHashMap<>(getOp);
        post.put("description", getOp.get("description") + "；POST 时 SQL 参数放 JSON body");
        post.put("requestBody", Map.of(
                "required", false,
                "content", Map.of("application/json", Map.of(
                        "schema", Map.of("type", "object", "additionalProperties", true)))));
        return post;
    }

    private static Map<String, Object> param(String name, String in, String type, boolean required, String desc) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("name", name);
        p.put("in", in);
        p.put("required", required);
        p.put("description", desc);
        p.put("schema", Map.of("type", type));
        return p;
    }

    private static Map<String, Object> errorResponse(String desc) {
        return Map.of("description", desc);
    }

    private static List<String> extractSqlParams(String sql) {
        Matcher matcher = NAMED_PARAM.matcher(sql);
        LinkedHashSet<String> names = new LinkedHashSet<>();
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return List.copyOf(names);
    }

    private static String schemaType(ApiVersion version, String name) {
        Map<String, Object> schema = version.getParamSchema();
        if (schema == null || !(schema.get(name) instanceof Map<?, ?> spec)) {
            return "string";
        }
        Object type = spec.get("type");
        if (type == null) {
            return "string";
        }
        String t = String.valueOf(type).toLowerCase();
        return switch (t) {
            case "integer", "int", "long" -> "integer";
            case "number", "double", "float", "decimal" -> "number";
            case "boolean", "bool" -> "boolean";
            default -> "string";
        };
    }
}
