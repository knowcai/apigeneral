package com.apigateway.service;

import com.apigateway.entity.ApiAccessLog;
import com.apigateway.repository.ApiAccessLogRepository;
import com.apigateway.security.AuthzService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccessLogService {

    private final ApiAccessLogRepository repository;
    private final ObjectMapper objectMapper;
    private final AuthzService authzService;

    @Async
    public void logAsync(String apiCode, Integer version, String clientIp, String consumerName,
                         Map<String, Object> params, String mode, long rows, long bytes,
                         long durationMs, String status, String error) {
        ApiAccessLog log = new ApiAccessLog();
        log.setRequestId(UUID.randomUUID().toString());
        log.setApiCode(apiCode);
        log.setApiVersion(version);
        log.setClientIp(clientIp);
        log.setConsumerName(consumerName);
        log.setRequestParams(params);
        log.setResponseMode(mode);
        log.setResponseRows(rows);
        log.setResponseBytes(bytes);
        log.setDurationMs(durationMs);
        log.setStatus(status);
        log.setErrorMessage(error);
        repository.save(log);
    }

    public Page<ApiAccessLog> list(String apiCode, Pageable pageable) {
        authzService.requireAuthenticated();
        Pageable sorted = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        if (apiCode == null || apiCode.isBlank()) {
            return repository.findAll(sorted);
        }
        return repository.findByApiCodeContainingIgnoreCase(apiCode, sorted);
    }

    public long estimateBytes(Object data) {
        try {
            return objectMapper.writeValueAsBytes(data).length;
        } catch (Exception e) {
            return 0;
        }
    }
}
