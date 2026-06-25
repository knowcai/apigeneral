package com.apigateway.service;

import com.apigateway.config.GatewayPoolProperties;
import com.apigateway.pool.GlobalConnectionBudget;
import com.apigateway.repository.ApiAccessLogRepository;
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

    public Map<String, Object> dashboard(int hours) {
        authzService.requireAuthenticated();
        int effectiveHours = Math.min(Math.max(hours, 1), 168);
        LocalDateTime since = LocalDateTime.now().minusHours(effectiveHours);

        long total = repository.countByCreatedAtAfter(since);
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (Object[] row : repository.countGroupByStatusSince(since)) {
            byStatus.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }
        long success = byStatus.getOrDefault("SUCCESS", 0L);
        double successRate = total > 0 ? success * 100.0 / total : 0;

        List<Map<String, Object>> topApis = new ArrayList<>();
        for (Object[] row : repository.topApisSince(since, PageRequest.of(0, 10))) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("apiCode", row[0]);
            item.put("count", ((Number) row[1]).longValue());
            topApis.add(item);
        }

        List<Map<String, Object>> hourly = new ArrayList<>();
        for (Object[] row : repository.hourlyCountsSince(since)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("hour", row[0] != null ? row[0].toString() : "");
            item.put("count", ((Number) row[1]).longValue());
            hourly.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hours", effectiveHours);
        result.put("totalCalls", total);
        result.put("successRate", Math.round(successRate * 100.0) / 100.0);
        result.put("avgDurationMs", Math.round(repository.avgDurationSince(since)));
        result.put("byStatus", byStatus);
        result.put("topApis", topApis);
        result.put("hourly", hourly);
        result.put("globalPool", globalConnectionBudget.snapshot());
        result.put("datasourcePools", connectionPoolManager.poolSnapshots());
        result.put("globalPoolConfig", Map.of(
                "globalMaxConnections", poolProperties.getGlobalMaxConnections(),
                "acquireTimeoutMs", poolProperties.getAcquireTimeoutMs()));
        return result;
    }

    public Map<String, Object> runtime() {
        authzService.requireAuthenticated();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("globalPool", globalConnectionBudget.snapshot());
        result.put("datasourcePools", connectionPoolManager.poolSnapshots());
        result.put("globalPoolConfig", Map.of(
                "globalMaxConnections", poolProperties.getGlobalMaxConnections(),
                "acquireTimeoutMs", poolProperties.getAcquireTimeoutMs()));
        return result;
    }
}
