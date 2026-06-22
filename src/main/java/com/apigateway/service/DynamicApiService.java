package com.apigateway.service;

import com.apigateway.dto.QueryResult;
import com.apigateway.entity.ApiDefinition;
import com.apigateway.entity.ApiVersion;
import com.apigateway.entity.Datasource;
import com.apigateway.exception.BusinessException;
import com.apigateway.repository.DatasourceRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class DynamicApiService {

    private final ApiManagementService apiManagementService;
    private final DatasourceRepository datasourceRepository;
    private final SqlExecutionService sqlExecutionService;
    private final AccessLogService accessLogService;
    private final GatewayProtectionService protectionService;
    private final InFlightRequestTracker inFlightRequestTracker;

    public QueryResult invoke(String theme, String apiCode, Integer versionNo, Map<String, Object> params,
                              Integer page, Integer pageSize, HttpServletRequest request) {
        inFlightRequestTracker.begin(apiCode);
        try {
            return invokeInternal(theme, apiCode, versionNo, params, page, pageSize, request);
        } finally {
            inFlightRequestTracker.end(apiCode);
        }
    }

    private QueryResult invokeInternal(String theme, String apiCode, Integer versionNo, Map<String, Object> params,
                              Integer page, Integer pageSize, HttpServletRequest request) {
        long start = System.currentTimeMillis();
        String clientIp = resolveClientIp(request);
        String consumer = request.getHeader("X-Consumer-Name");
        if (consumer == null) {
            consumer = "anonymous";
        }
        Map<String, Object> mergedParams = mergeParams(params);
        Integer logVersion = versionNo;
        boolean sqlAttempted = false;

        try {
            ApiDefinition def = apiManagementService.getByCode(apiCode);
            if (theme != null && def.getTheme() != null && !theme.equals(def.getTheme())) {
                throw new BusinessException("主题路径不匹配");
            }
            ApiVersion version = apiManagementService.resolvePublishedVersion(def, versionNo);
            logVersion = version.getVersionNo();
            assertIpAllowed(version, clientIp);

            Map<String, Object> config = version.getResponseConfig() != null ? version.getResponseConfig() : Map.of();
            protectionService.checkRateLimit(clientIp, apiCode, protectionService.resolveApiQpsOverride(config));
            // 单 API 熔断：仅当该 apiCode 处于 OPEN 状态时拦截，不影响其他 API
            protectionService.checkCircuitOrFallback(apiCode);

            Datasource ds = datasourceRepository.findById(version.getDatasourceId())
                    .orElseThrow(() -> new BusinessException("数据源不存在"));
            if (Boolean.TRUE.equals(ds.getReadonly())) {
                JdbcUrlBuilder.assertReadOnly(version.getSqlTemplate());
            }

            PaginationParams pagination = resolvePaginationParams(config, page, pageSize);

            sqlAttempted = true;
            QueryResult result = protectionService.executeWithRetry(() ->
                    sqlExecutionService.execute(ds, version.getSqlTemplate(), mergedParams,
                            config, pagination.page(), pagination.pageSize()));

            protectionService.onSuccess(apiCode);
            long bytes = accessLogService.estimateBytes(result);
            logAccess(apiCode, logVersion, clientIp, consumer, mergedParams, start,
                    result.getRows().size(), bytes, "SUCCESS", null);
            return result;
        } catch (BusinessException e) {
            if (sqlAttempted && shouldCountExecutionFailure(e)) {
                protectionService.onFailure(apiCode);
            }
            logAccess(apiCode, logVersion, clientIp, consumer, mergedParams, start,
                    0, 0, statusLabel(e), e.getMessage());
            throw e;
        } catch (Exception e) {
            if (sqlAttempted) {
                protectionService.onFailure(apiCode);
            }
            logAccess(apiCode, logVersion, clientIp, consumer, mergedParams, start,
                    0, 0, "ERROR", e.getMessage());
            throw e;
        }
    }

    private void logAccess(String apiCode, Integer version, String clientIp, String consumer,
                           Map<String, Object> params, long start,
                           long rows, long bytes, String status, String error) {
        accessLogService.logAsync(apiCode, version, clientIp, consumer, params,
                "PAGE", rows, bytes, System.currentTimeMillis() - start, status, error);
    }

    private boolean shouldCountExecutionFailure(BusinessException e) {
        return e.getCode() != 429 && e.getCode() != 403;
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

    private Map<String, Object> mergeParams(Map<String, Object> requestParams) {
        return requestParams != null ? requestParams : Map.of();
    }

    private record PaginationParams(int page, int pageSize) {
    }

    private PaginationParams resolvePaginationParams(Map<String, Object> config, Integer page, Integer pageSize) {
        if (page == null) {
            throw new BusinessException("缺少分页参数: page");
        }
        if (pageSize == null) {
            throw new BusinessException("缺少分页参数: pageSize");
        }
        if (page < 1) {
            throw new BusinessException("分页参数 page 必须 >= 1");
        }
        if (pageSize < 1) {
            throw new BusinessException("分页参数 pageSize 必须 >= 1");
        }
        int maxPageSize = intConfig(config, "maxPageSize", 500);
        if (pageSize > maxPageSize) {
            throw new BusinessException("分页参数 pageSize 不能超过 " + maxPageSize);
        }
        return new PaginationParams(page, pageSize);
    }

    private int intConfig(Map<String, Object> config, String key, int defaultValue) {
        if (config == null) {
            return defaultValue;
        }
        Object v = config.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        return defaultValue;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
