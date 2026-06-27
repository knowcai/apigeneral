package com.apigateway.service;

import com.apigateway.config.ConsumerKeyProperties;
import com.apigateway.entity.ApiDefinition;
import com.apigateway.entity.Theme;
import com.apigateway.config.GatewayPoolProperties;
import com.apigateway.pool.GlobalConnectionBudget;
import com.apigateway.repository.ApiAccessLogRepository;
import com.apigateway.repository.ApiDefinitionRepository;
import com.apigateway.repository.ThemeRepository;
import com.apigateway.security.AuthzService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MonitoringService {

    private final ApiAccessLogRepository repository;
    private final AuthzService authzService;
    private final GlobalConnectionBudget globalConnectionBudget;
    private final ConnectionPoolManager connectionPoolManager;
    private final GatewayPoolProperties poolProperties;
    private final CircuitBreakerService circuitBreakerService;
    private final ApiDefinitionRepository apiDefinitionRepository;
    private final ThemeRepository themeRepository;
    private final ObservabilityScopeService observabilityScopeService;
    private final ConsumerKeyProperties consumerKeyProperties;
    private final ThemeService themeService;

    public Map<String, Object> dashboard(int hours) {
        ObservabilityScopeService.Scope scope = observabilityScopeService.currentScope();
        int effectiveHours = Math.min(Math.max(hours, 1), 168);
        LocalDateTime since = LocalDateTime.now().minusHours(effectiveHours);

        if (scope.isEmpty()) {
            return emptyDashboard(effectiveHours, scope);
        }

        List<String> apiCodes = scope.global() ? null : List.copyOf(scope.apiCodes());
        long total = scope.global() ? repository.countByCreatedAtAfter(since)
                : repository.countByCreatedAtAfterAndApiCodeIn(since, apiCodes);

        Map<String, Long> byStatus = new LinkedHashMap<>();
        List<Object[]> statusRows = scope.global()
                ? repository.countGroupByStatusSince(since)
                : repository.countGroupByStatusSinceAndApiCodeIn(since, apiCodes);
        for (Object[] row : statusRows) {
            byStatus.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }
        long success = byStatus.getOrDefault("SUCCESS", 0L);
        double successRate = total > 0 ? success * 100.0 / total : 0;

        List<Map<String, Object>> topApis = new ArrayList<>();
        List<Object[]> topRows = scope.global()
                ? repository.topApisSince(since, PageRequest.of(0, 10))
                : repository.topApisSinceAndApiCodeIn(since, apiCodes, PageRequest.of(0, 10));
        for (Object[] row : topRows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("apiCode", row[0]);
            item.put("count", ((Number) row[1]).longValue());
            topApis.add(item);
        }

        List<Map<String, Object>> hourly = new ArrayList<>();
        List<Object[]> hourlyRows = scope.global()
                ? repository.hourlyCountsSince(since)
                : repository.hourlyCountsSinceAndApiCodeIn(since, apiCodes);
        for (Object[] row : hourlyRows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("hour", row[0] != null ? row[0].toString() : "");
            item.put("count", ((Number) row[1]).longValue());
            hourly.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scoped", !scope.global());
        result.put("hours", effectiveHours);
        result.put("totalCalls", total);
        result.put("successRate", Math.round(successRate * 100.0) / 100.0);
        result.put("avgDurationMs", Math.round(scope.global()
                ? repository.avgDurationSince(since)
                : repository.avgDurationSinceAndApiCodeIn(since, apiCodes)));
        result.put("byStatus", byStatus);
        result.put("rateLimitedCalls", byStatus.getOrDefault("RATE_LIMITED", 0L));
        result.put("circuitOpenCalls", byStatus.getOrDefault("CIRCUIT_OPEN", 0L));
        result.put("topApis", topApis);

        List<Map<String, Object>> topRateLimited = new ArrayList<>();
        List<Object[]> rateRows = scope.global()
                ? repository.topApisByStatusSince(since, "RATE_LIMITED", PageRequest.of(0, 10))
                : repository.topApisByStatusSinceAndApiCodeIn(since, apiCodes, "RATE_LIMITED", PageRequest.of(0, 10));
        for (Object[] row : rateRows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("apiCode", row[0]);
            item.put("count", ((Number) row[1]).longValue());
            topRateLimited.add(item);
        }
        result.put("topRateLimitedApis", topRateLimited);

        List<Map<String, Object>> circuitStates = buildCircuitStates(scope);
        result.put("circuitStates", circuitStates);
        result.put("hourly", hourly);

        appendDisabledThemeMetrics(result, since, scope);
        appendPoolMetrics(result, scope);
        appendApiKeyUsageMetrics(result, since, scope);

        if (scope.global() && consumerKeyProperties.isLegacyEnabled()) {
            appendLegacyKeyMetrics(result, since);
        }
        return result;
    }

    private void appendLegacyKeyMetrics(Map<String, Object> result, LocalDateTime since) {
        long legacy = repository.countByCreatedAtAfterAndAuthMode(since, "LEGACY");
        long themeKey = repository.countByCreatedAtAfterAndAuthMode(since, "THEME_KEY");
        long known = legacy + themeKey;
        result.put("legacyKeyCalls", legacy);
        result.put("themeKeyCalls", themeKey);
        result.put("legacyKeyPercent", known > 0 ? Math.round(legacy * 1000.0 / known) / 10.0 : 0);
        result.put("legacyEnabled", consumerKeyProperties.isLegacyEnabled());
        result.put("legacySunsetDate", consumerKeyProperties.getLegacySunsetDate());
    }

    private void appendDisabledThemeMetrics(Map<String, Object> result, LocalDateTime since,
                                            ObservabilityScopeService.Scope scope) {
        List<Theme> disabledThemes = themeRepository.findByEnabledFalse();
        if (!scope.global()) {
            List<Long> accessible = themeService.accessibleThemeIds();
            disabledThemes = disabledThemes.stream().filter(t -> accessible.contains(t.getId())).toList();
        }
        if (!disabledThemes.isEmpty()) {
            List<Long> disabledThemeIds = disabledThemes.stream().map(Theme::getId).toList();
            List<String> disabledApiCodes = apiDefinitionRepository.findByThemeIdIn(disabledThemeIds).stream()
                    .map(ApiDefinition::getApiCode)
                    .filter(code -> scope.global() || scope.apiCodes().contains(code))
                    .toList();
            long disabledThemeCalls = disabledApiCodes.isEmpty() ? 0L
                    : repository.countByCreatedAtAfterAndApiCodeIn(since, disabledApiCodes);
            result.put("disabledThemeCalls", disabledThemeCalls);
            result.put("disabledThemeCount", disabledThemes.size());
        } else {
            result.put("disabledThemeCalls", 0L);
            result.put("disabledThemeCount", 0);
        }
    }

    private void appendPoolMetrics(Map<String, Object> result, ObservabilityScopeService.Scope scope) {
        if (scope.global()) {
            result.put("globalPool", globalConnectionBudget.snapshot());
            result.put("globalPoolConfig", Map.of(
                    "globalMaxConnections", poolProperties.getGlobalMaxConnections(),
                    "acquireTimeoutMs", poolProperties.getAcquireTimeoutMs()));
        }
        List<Map<String, Object>> pools = connectionPoolManager.poolSnapshots().stream()
                .filter(p -> scope.global() || scope.datasourceIds().contains(((Number) p.get("datasourceId")).longValue()))
                .toList();
        result.put("datasourcePools", pools);
    }

    private void appendApiKeyUsageMetrics(Map<String, Object> result, LocalDateTime since,
                                          ObservabilityScopeService.Scope scope) {
        List<Object[]> rows = scope.global()
                ? repository.apiKeyUsageSince(since, PageRequest.of(0, 50))
                : repository.apiKeyUsageSinceAndApiCodeIn(since, List.copyOf(scope.apiCodes()), PageRequest.of(0, 50));
        List<Map<String, Object>> usage = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("apiCode", row[0]);
            item.put("consumerName", row[1]);
            item.put("consumerId", row[2] != null ? ((Number) row[2]).longValue() : null);
            item.put("count", ((Number) row[3]).longValue());
            usage.add(item);
        }
        result.put("apiKeyUsage", usage);
    }

    private List<Map<String, Object>> buildCircuitStates(ObservabilityScopeService.Scope scope) {
        List<ApiDefinition> defs = scope.global()
                ? apiDefinitionRepository.findAll()
                : apiDefinitionRepository.findAll().stream()
                        .filter(d -> scope.apiCodes().contains(d.getApiCode()))
                        .toList();
        List<Map<String, Object>> circuitStates = new ArrayList<>();
        for (var def : defs) {
            Map<String, Object> snap = circuitBreakerService.describeState(def.getApiCode());
            String cbStatus = String.valueOf(snap.get("status"));
            int windowFailures = ((Number) snap.getOrDefault("windowFailures", 0)).intValue();
            if (!"CLOSED".equals(cbStatus) || windowFailures > 0) {
                circuitStates.add(snap);
            }
        }
        circuitStates.sort((a, b) -> {
            int rankA = circuitRank(String.valueOf(a.get("status")));
            int rankB = circuitRank(String.valueOf(b.get("status")));
            if (rankA != rankB) {
                return Integer.compare(rankA, rankB);
            }
            return Double.compare(
                    ((Number) b.getOrDefault("failureRatePercent", 0)).doubleValue(),
                    ((Number) a.getOrDefault("failureRatePercent", 0)).doubleValue());
        });
        return circuitStates;
    }

    private Map<String, Object> emptyDashboard(int hours, ObservabilityScopeService.Scope scope) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scoped", !scope.global());
        result.put("hours", hours);
        result.put("totalCalls", 0L);
        result.put("successRate", 0);
        result.put("avgDurationMs", 0L);
        result.put("byStatus", Map.of());
        result.put("rateLimitedCalls", 0L);
        result.put("circuitOpenCalls", 0L);
        result.put("topApis", List.of());
        result.put("topRateLimitedApis", List.of());
        result.put("circuitStates", List.of());
        result.put("hourly", List.of());
        result.put("apiKeyUsage", List.of());
        result.put("disabledThemeCalls", 0L);
        result.put("disabledThemeCount", 0);
        result.put("datasourcePools", List.of());
        if (scope.global()) {
            result.put("globalPool", globalConnectionBudget.snapshot());
            result.put("globalPoolConfig", Map.of(
                    "globalMaxConnections", poolProperties.getGlobalMaxConnections(),
                    "acquireTimeoutMs", poolProperties.getAcquireTimeoutMs()));
            if (consumerKeyProperties.isLegacyEnabled()) {
                appendLegacyKeyMetrics(result, LocalDateTime.now());
            }
        }
        return result;
    }

    private static int circuitRank(String status) {
        return switch (status) {
            case "OPEN" -> 0;
            case "HALF_OPEN" -> 1;
            default -> 2;
        };
    }

    public Map<String, Object> runtime() {
        ObservabilityScopeService.Scope scope = observabilityScopeService.currentScope();
        Map<String, Object> result = new LinkedHashMap<>();
        if (scope.global()) {
            result.put("globalPool", globalConnectionBudget.snapshot());
            result.put("globalPoolConfig", Map.of(
                    "globalMaxConnections", poolProperties.getGlobalMaxConnections(),
                    "acquireTimeoutMs", poolProperties.getAcquireTimeoutMs()));
        }
        List<Map<String, Object>> pools = connectionPoolManager.poolSnapshots().stream()
                .filter(p -> scope.global() || scope.datasourceIds().contains(((Number) p.get("datasourceId")).longValue()))
                .toList();
        result.put("datasourcePools", pools);
        return result;
    }
}
