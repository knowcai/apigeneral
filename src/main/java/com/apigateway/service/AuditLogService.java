package com.apigateway.service;

import com.apigateway.entity.AuditLog;
import com.apigateway.entity.ThemeMembershipRole;
import com.apigateway.repository.AuditLogRepository;
import com.apigateway.repository.ThemeMembershipRepository;
import com.apigateway.security.AuthUser;
import com.apigateway.security.AuthzService;
import com.apigateway.security.CurrentUser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private static final Set<String> DETAIL_SECRET_FIELDS = Set.of(
            "password", "apiKeyHash", "encryptedKey", "apiKey");

    private final AuditLogRepository repository;
    private final CurrentUser currentUser;
    private final ObjectMapper objectMapper;
    private final AuthzService authzService;
    private final ThemeMembershipRepository membershipRepository;

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
                log.setDetail(objectMapper.writeValueAsString(sanitizeDetail(detail)));
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
                log.setDetail(objectMapper.writeValueAsString(sanitizeDetail(detail)));
            } catch (Exception e) {
                log.setDetail(String.valueOf(detail));
            }
        }
        repository.save(log);
    }

    public Page<AuditLog> list(Pageable pageable) {
        authzService.requireSuperAdmin();
        return repository.findAllByOrderByCreatedAtDesc(pageable)
                .map(log -> {
                    log.setDetail(sanitizeDetailJson(log.getDetail()));
                    return log;
                });
    }

    public Page<Map<String, Object>> listThemeApiKeyEvents(Pageable pageable) {
        authzService.requireAuthenticated();
        Long uid = currentUser.requireUser().getId();
        Page<AuditLog> page = repository.findByResourceTypeAndActionInOrderByCreatedAtDesc(
                "THEME_API_KEY", List.of("CREATE", "DELETE"), pageable);
        List<Map<String, Object>> views = page.getContent().stream()
                .map(this::toThemeApiKeyEventView)
                .filter(m -> eventVisibleToUser(m, uid))
                .toList();
        return new PageImpl<>(views, pageable, authzService.isSuperAdmin() ? page.getTotalElements() : views.size());
    }

    private boolean eventVisibleToUser(Map<String, Object> event, Long uid) {
        if (authzService.isSuperAdmin()) {
            return true;
        }
        Object themeId = event.get("themeId");
        if (themeId instanceof Number n) {
            return membershipRepository.findByThemeIdAndUserId(n.longValue(), uid)
                    .map(m -> m.getRole() == ThemeMembershipRole.THEME_ADMIN)
                    .orElse(false);
        }
        return false;
    }

    private Map<String, Object> toThemeApiKeyEventView(AuditLog log) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", log.getId());
        m.put("action", log.getAction());
        m.put("username", log.getUsername());
        m.put("resourceId", log.getResourceId());
        m.put("resourceName", log.getResourceName());
        m.put("themeId", parseThemeIdFromResourceName(log.getResourceName()));
        m.put("createdAt", log.getCreatedAt());
        m.put("viaApproval", parseViaApproval(log.getDetail()));
        return m;
    }

    private Long parseThemeIdFromResourceName(String resourceName) {
        if (resourceName == null || !resourceName.contains(":")) {
            return null;
        }
        try {
            return Long.parseLong(resourceName.split(":")[0]);
        } catch (NumberFormatException e) {
            return null;
        }
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

    private Object sanitizeDetail(Object detail) {
        if (detail == null) {
            return null;
        }
        try {
            Map<String, Object> map = objectMapper.convertValue(detail, new TypeReference<>() {});
            return redactMap(map);
        } catch (Exception e) {
            return detail;
        }
    }

    private String sanitizeDetailJson(String detail) {
        if (detail == null || detail.isBlank()) {
            return detail;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(detail, new TypeReference<>() {});
            return objectMapper.writeValueAsString(redactMap(map));
        } catch (Exception e) {
            return detail;
        }
    }

    private Map<String, Object> redactMap(Map<String, Object> map) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String key = e.getKey();
            if (DETAIL_SECRET_FIELDS.contains(key) || key.toLowerCase().contains("password")) {
                out.put(key, "***");
            } else if (e.getValue() instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) nested;
                out.put(key, redactMap(nestedMap));
            } else {
                out.put(key, e.getValue());
            }
        }
        return out;
    }
}
