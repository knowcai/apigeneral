package com.apigateway.service;

import com.apigateway.entity.AuditLog;
import com.apigateway.repository.AuditLogRepository;
import com.apigateway.security.AuthUser;
import com.apigateway.security.AuthzService;
import com.apigateway.security.CurrentUser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository repository;
    private final CurrentUser currentUser;
    private final ObjectMapper objectMapper;
    private final AuthzService authzService;

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
        authzService.requireSuperAdmin();
        return repository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public Page<Map<String, Object>> listThemeApiKeyEvents(Pageable pageable) {
        authzService.requireAuthenticated();
        return repository.findByResourceTypeAndActionInOrderByCreatedAtDesc(
                        "THEME_API_KEY", List.of("CREATE", "ROTATE_KEY"), pageable)
                .map(this::toThemeApiKeyEventView);
    }

    private Map<String, Object> toThemeApiKeyEventView(AuditLog log) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", log.getId());
        m.put("action", log.getAction());
        m.put("username", log.getUsername());
        m.put("resourceId", log.getResourceId());
        m.put("resourceName", log.getResourceName());
        m.put("createdAt", log.getCreatedAt());
        m.put("viaApproval", parseViaApproval(log.getDetail()));
        return m;
    }

    private boolean parseViaApproval(String detail) {
        if (detail == null || detail.isBlank()) {
            return false;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(detail, new TypeReference<>() {});
            return Boolean.TRUE.equals(map.get("viaApproval"));
        } catch (Exception e) {
            return false;
        }
    }
}
