package com.apigateway.service;

import com.apigateway.entity.AuditLog;
import com.apigateway.repository.AuditLogRepository;
import com.apigateway.security.AuthUser;
import com.apigateway.security.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository repository;
    private final CurrentUser currentUser;
    private final ObjectMapper objectMapper;

    @Async
    public void logAsync(String action, String resourceType, String resourceId, String resourceName, Object detail) {
        AuditLog log = new AuditLog();
        try {
            AuthUser user = currentUser.requireUser();
            log.setUserId(user.getId());
            log.setUsername(user.getUsername());
        } catch (Exception ignored) {
            log.setUsername("system");
        }
        log.setAction(action);
        log.setResourceType(resourceType);
        log.setResourceId(resourceId);
        log.setResourceName(resourceName);
        if (detail != null) {
            try {
                log.setDetail(objectMapper.writeValueAsString(detail));
            } catch (Exception e) {
                log.setDetail(String.valueOf(detail));
            }
        }
        repository.save(log);
    }

    public void log(String action, String resourceType, String resourceId, String resourceName, Object detail) {
        logAsync(action, resourceType, resourceId, resourceName, detail);
    }

    public void logAs(Long userId, String username, String action, String resourceType,
                      String resourceId, String resourceName, Object detail) {
        AuditLog log = new AuditLog();
        log.setUserId(userId);
        log.setUsername(username);
        log.setAction(action);
        log.setResourceType(resourceType);
        log.setResourceId(resourceId);
        log.setResourceName(resourceName);
        if (detail != null) {
            try {
                log.setDetail(objectMapper.writeValueAsString(detail));
            } catch (Exception e) {
                log.setDetail(String.valueOf(detail));
            }
        }
        repository.save(log);
    }

    public Page<AuditLog> list(Pageable pageable) {
        return repository.findAllByOrderByCreatedAtDesc(pageable);
    }
}
