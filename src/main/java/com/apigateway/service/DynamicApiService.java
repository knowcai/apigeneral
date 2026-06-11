package com.apigateway.service;

import com.apigateway.dto.QueryResult;
import com.apigateway.entity.ApiDefinition;
import com.apigateway.entity.ApiVersion;
import com.apigateway.entity.Datasource;
import com.apigateway.entity.ResponseMode;
import com.apigateway.exception.BusinessException;
import com.apigateway.repository.DatasourceRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.*;

@Service
@RequiredArgsConstructor
public class DynamicApiService {

    private final ApiManagementService apiManagementService;
    private final DatasourceRepository datasourceRepository;
    private final SqlExecutionService sqlExecutionService;
    private final AccessLogService accessLogService;
    private final GatewayProtectionService protectionService;

    public Object invoke(String theme, String apiCode, Integer versionNo, Map<String, Object> params,
                         int page, int pageSize, int chunkIndex, HttpServletRequest request) {
        long start = System.currentTimeMillis();
        String clientIp = resolveClientIp(request);
        String consumer = request.getHeader("X-Consumer-Name");
        if (consumer == null) {
            consumer = "anonymous";
        }

        ApiDefinition def = apiManagementService.getByCode(apiCode);
        if (theme != null && def.getTheme() != null && !theme.equals(def.getTheme())) {
            throw new BusinessException("主题路径不匹配");
        }
        ApiVersion version = apiManagementService.resolvePublishedVersion(def, versionNo);
        assertIpAllowed(version, clientIp);

        Map<String, Object> config = version.getResponseConfig() != null ? version.getResponseConfig() : Map.of();
        protectionService.checkRateLimit(clientIp, apiCode, protectionService.resolveApiQpsOverride(config));
        protectionService.checkCircuitOrFallback(apiCode);

        Datasource ds = datasourceRepository.findById(version.getDatasourceId())
                .orElseThrow(() -> new BusinessException("数据源不存在"));
        if (Boolean.TRUE.equals(ds.getReadonly())) {
            JdbcUrlBuilder.assertReadOnly(version.getSqlTemplate());
        }

        Map<String, Object> mergedParams = mergeParams(version, params);
        ResponseMode mode = version.getResponseMode();

        try {
            Object result = protectionService.executeWithRetry(() -> {
                if (mode == ResponseMode.STREAM) {
                    return sqlExecutionService.stream(ds, version.getSqlTemplate(), mergedParams, config);
                }
                return sqlExecutionService.execute(ds, version.getSqlTemplate(), mergedParams,
                        mode, config, page, pageSize, chunkIndex);
            });

            protectionService.onSuccess(apiCode);
            if (mode == ResponseMode.STREAM) {
                accessLogService.logAsync(apiCode, version.getVersionNo(), clientIp, consumer, mergedParams,
                        mode.name(), 0, 0, System.currentTimeMillis() - start, "STREAMING", null);
                return result;
            }

            QueryResult queryResult = (QueryResult) result;
            long bytes = accessLogService.estimateBytes(queryResult);
            accessLogService.logAsync(apiCode, version.getVersionNo(), clientIp, consumer, mergedParams,
                    mode.name(), queryResult.getRows().size(), bytes, System.currentTimeMillis() - start, "SUCCESS", null);
            return queryResult;
        } catch (BusinessException e) {
            if (e.getCode() != 429 && e.getCode() != 403) {
                protectionService.onFailure(apiCode);
            }
            accessLogService.logAsync(apiCode, version.getVersionNo(), clientIp, consumer, mergedParams,
                    mode.name(), 0, 0, System.currentTimeMillis() - start, statusLabel(e), e.getMessage());
            throw e;
        } catch (Exception e) {
            protectionService.onFailure(apiCode);
            accessLogService.logAsync(apiCode, version.getVersionNo(), clientIp, consumer, mergedParams,
                    mode.name(), 0, 0, System.currentTimeMillis() - start, "ERROR", e.getMessage());
            throw e;
        }
    }

    private String statusLabel(BusinessException e) {
        return switch (e.getCode()) {
            case 429 -> "RATE_LIMITED";
            case 503 -> "CIRCUIT_OPEN";
            case 403 -> "FORBIDDEN";
            default -> "ERROR";
        };
    }

    @SuppressWarnings("unchecked")
    private void assertIpAllowed(ApiVersion version, String clientIp) {
        Map<String, Object> config = version.getResponseConfig();
        if (config == null || !config.containsKey("ipWhitelist")) {
            return;
        }
        Object raw = config.get("ipWhitelist");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        boolean allowed = list.stream().map(String::valueOf).anyMatch(ip -> ip.equals(clientIp) || "*".equals(ip));
        if (!allowed) {
            throw new BusinessException(403, "IP 不在白名单: " + clientIp);
        }
    }

    private Map<String, Object> mergeParams(ApiVersion version, Map<String, Object> requestParams) {
        Map<String, Object> merged = new HashMap<>();
        if (version.getParamSchema() != null) {
            version.getParamSchema().forEach((k, v) -> {
                if (v instanceof Map<?, ?> schema && schema.containsKey("default")) {
                    merged.put(k, schema.get("default"));
                }
            });
        }
        if (requestParams != null) {
            merged.putAll(requestParams);
        }
        return merged;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
