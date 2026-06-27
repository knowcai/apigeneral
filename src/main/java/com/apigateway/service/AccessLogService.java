package com.apigateway.service;

import com.apigateway.dto.AccessLogQuery;
import com.apigateway.dto.AccessLogView;
import com.apigateway.entity.ApiAccessLog;
import com.apigateway.repository.ApiAccessLogRepository;
import com.apigateway.security.AuthzService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccessLogService {

    private static final int EXPORT_MAX_ROWS = 10_000;
    private static final DateTimeFormatter CSV_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> SENSITIVE_PARAM_KEYS = Set.of(
            "password", "token", "secret", "apikey", "api_key", "authorization");

    private final ApiAccessLogRepository repository;
    private final ObjectMapper objectMapper;
    private final AuthzService authzService;
    private final ObservabilityScopeService observabilityScopeService;

    @Async
    public void logAsync(String apiCode, Integer version, String clientIp, Long consumerId, String consumerName,
                         String authMode, Map<String, Object> params, String mode, long rows, long bytes,
                         long durationMs, String status, String error) {
        ApiAccessLog log = new ApiAccessLog();
        log.setRequestId(UUID.randomUUID().toString());
        log.setApiCode(apiCode);
        log.setApiVersion(version);
        log.setClientIp(clientIp);
        log.setConsumerId(consumerId);
        log.setConsumerName(consumerName);
        log.setAuthMode(authMode);
        log.setRequestParams(params);
        log.setResponseMode(mode);
        log.setResponseRows(rows);
        log.setResponseBytes(bytes);
        log.setDurationMs(durationMs);
        log.setStatus(status);
        log.setErrorMessage(error);
        repository.save(log);
    }

    public Page<AccessLogView> search(AccessLogQuery query) {
        authzService.requireAuthenticated();
        Pageable sorted = PageRequest.of(
                Math.max(query.getPage(), 0),
                Math.min(Math.max(query.getSize(), 1), 200),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return repository.findAll(buildSpec(query), sorted).map(this::toView);
    }

    public Page<AccessLogView> list(String apiCode, Pageable pageable) {
        AccessLogQuery query = new AccessLogQuery();
        query.setApiCode(apiCode);
        query.setPage(pageable.getPageNumber());
        query.setSize(pageable.getPageSize());
        return search(query);
    }

    public String exportCsv(AccessLogQuery query) {
        authzService.requireAuthenticated();
        Pageable pageable = PageRequest.of(0, EXPORT_MAX_ROWS, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<ApiAccessLog> rows = repository.findAll(buildSpec(query), pageable).getContent();
        StringBuilder sb = new StringBuilder();
        sb.append("time,apiCode,version,consumer,clientIp,status,rows,bytes,durationMs,error\n");
        for (ApiAccessLog row : rows) {
            sb.append(csv(row.getCreatedAt() != null ? CSV_TIME.format(row.getCreatedAt()) : "")).append(',');
            sb.append(csv(row.getApiCode())).append(',');
            sb.append(row.getApiVersion() != null ? row.getApiVersion() : "").append(',');
            sb.append(csv(row.getConsumerName())).append(',');
            sb.append(csv(row.getClientIp())).append(',');
            sb.append(csv(row.getStatus())).append(',');
            sb.append(row.getResponseRows() != null ? row.getResponseRows() : 0).append(',');
            sb.append(row.getResponseBytes() != null ? row.getResponseBytes() : 0).append(',');
            sb.append(row.getDurationMs() != null ? row.getDurationMs() : 0).append(',');
            sb.append(csv(row.getErrorMessage())).append('\n');
        }
        return sb.toString();
    }

    public long estimateBytes(Object data) {
        try {
            return objectMapper.writeValueAsBytes(data).length;
        } catch (Exception e) {
            return 0;
        }
    }

    private AccessLogView toView(ApiAccessLog row) {
        return AccessLogView.builder()
                .id(row.getId())
                .requestId(row.getRequestId())
                .apiCode(row.getApiCode())
                .apiVersion(row.getApiVersion())
                .consumerId(row.getConsumerId())
                .consumerName(row.getConsumerName())
                .clientIp(row.getClientIp())
                .requestParams(redactParams(row.getRequestParams()))
                .responseMode(row.getResponseMode())
                .responseRows(row.getResponseRows())
                .responseBytes(row.getResponseBytes())
                .durationMs(row.getDurationMs())
                .status(row.getStatus())
                .errorMessage(row.getErrorMessage())
                .authMode(row.getAuthMode())
                .createdAt(row.getCreatedAt())
                .build();
    }

    static Map<String, Object> redactParams(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return params;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : params.entrySet()) {
            String key = e.getKey();
            if (key != null && isSensitiveKey(key)) {
                out.put(key, "***");
            } else {
                out.put(key, e.getValue());
            }
        }
        return out;
    }

    private static boolean isSensitiveKey(String key) {
        String lower = key.toLowerCase();
        if (SENSITIVE_PARAM_KEYS.contains(lower)) {
            return true;
        }
        return lower.contains("password") || lower.contains("secret") || lower.contains("token");
    }

    private Specification<ApiAccessLog> buildSpec(AccessLogQuery query) {
        ObservabilityScopeService.Scope scope = observabilityScopeService.currentScope();
        return (root, cq, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (!scope.global()) {
                if (scope.apiCodes().isEmpty()) {
                    preds.add(cb.disjunction());
                } else {
                    preds.add(root.get("apiCode").in(scope.apiCodes()));
                }
            }
            if (query.getApiCode() != null && !query.getApiCode().isBlank()) {
                preds.add(cb.like(cb.lower(root.get("apiCode")),
                        "%" + query.getApiCode().trim().toLowerCase() + "%"));
            }
            if (query.getStatus() != null && !query.getStatus().isBlank()) {
                preds.add(cb.equal(root.get("status"), query.getStatus().trim()));
            }
            if (query.getConsumerName() != null && !query.getConsumerName().isBlank()) {
                preds.add(cb.like(cb.lower(root.get("consumerName")),
                        "%" + query.getConsumerName().trim().toLowerCase() + "%"));
            }
            if (query.getFrom() != null) {
                preds.add(cb.greaterThanOrEqualTo(root.get("createdAt"), query.getFrom()));
            }
            if (query.getTo() != null) {
                preds.add(cb.lessThanOrEqualTo(root.get("createdAt"), query.getTo()));
            }
            return cb.and(preds.toArray(Predicate[]::new));
        };
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
