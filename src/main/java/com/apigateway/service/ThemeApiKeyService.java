package com.apigateway.service;

import com.apigateway.config.ConsumerKeyProperties;
import com.apigateway.dto.ConsumerCreateResponse;
import com.apigateway.dto.ConsumerResponse;
import com.apigateway.dto.ThemeApiKeyRequest;
import com.apigateway.entity.*;
import com.apigateway.exception.BusinessException;
import com.apigateway.repository.ApiDefinitionRepository;
import com.apigateway.repository.ConsumerRepository;
import com.apigateway.repository.ThemeRepository;
import com.apigateway.security.ApiKeySupport;
import com.apigateway.security.AuthzService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ThemeApiKeyService {

    private final ConsumerRepository consumerRepository;
    private final ThemeRepository themeRepository;
    private final ApiDefinitionRepository apiDefinitionRepository;
    private final ThemeService themeService;
    private final AuthzService authzService;
    private final AuditLogService auditLogService;
    private final ConsumerKeyProperties keyProperties;
    private final ObjectMapper objectMapper;
    private final @Lazy ApprovalService approvalService;
    private final ThemeApiKeyPickupService pickupService;

    public Optional<Consumer> authenticate(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return Optional.empty();
        }
        String trimmed = rawKey.trim();
        Optional<Consumer> found = consumerRepository.findByApiKeyHash(
                        ApiKeySupport.hashKey(trimmed, keyProperties.getKeyPepper()))
                .filter(c -> "ACTIVE".equals(c.getStatus()));
        if (found.isPresent()) {
            Consumer consumer = found.get();
            if (consumer.getThemeId() == null && !keyProperties.isLegacyEnabled()) {
                return Optional.empty();
            }
            return found;
        }
        if (!keyProperties.isLegacyEnabled()) {
            return Optional.empty();
        }
        return consumerRepository.findByApiKeyHash(ApiKeySupport.legacyHashKey(trimmed))
                .filter(c -> "ACTIVE".equals(c.getStatus()));
    }

    public ConsumerResponse getByTheme(Long themeId) {
        themeService.requireThemeRead(themeId);
        return consumerRepository.findByThemeId(themeId)
                .map(c -> toResponse(c, themeId))
                .orElse(null);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public ConsumerCreateResponse create(Long themeId, ThemeApiKeyRequest req) {
        themeService.requireThemeAdmin(themeId);
        themeRepository.findById(themeId).orElseThrow(() -> new BusinessException("主题不存在"));
        if (consumerRepository.existsByThemeId(themeId)) {
            throw new BusinessException("该主题已有 API Key，请使用编辑或轮换");
        }
        if (!authzService.isSuperAdmin()) {
            approvalService.submit(ApprovalResourceType.THEME_API_KEY, null, ApprovalAction.CREATE,
                    themeId, "创建主题 API Key: " + req.getName().trim(), req);
            throw new BusinessException(202, "已提交审批，请在「审批中心」查看进度");
        }
        return createDirect(themeId, req);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public ConsumerResponse update(Long themeId, ThemeApiKeyRequest req) {
        themeService.requireThemeAdmin(themeId);
        Consumer consumer = requireThemeConsumer(themeId);
        if (!authzService.isSuperAdmin()) {
            approvalService.submit(ApprovalResourceType.THEME_API_KEY, consumer.getId(), ApprovalAction.UPDATE,
                    themeId, "更新主题 API Key: " + consumer.getName(), req);
            throw new BusinessException(202, "已提交审批，请在「审批中心」查看进度");
        }
        return updateDirect(consumer, req);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public ConsumerCreateResponse rotate(Long themeId) {
        themeService.requireThemeAdmin(themeId);
        Consumer consumer = requireThemeConsumer(themeId);
        if (!authzService.isSuperAdmin()) {
            approvalService.submit(ApprovalResourceType.THEME_API_KEY, consumer.getId(), ApprovalAction.ROTATE_KEY,
                    themeId, "轮换主题 API Key: " + consumer.getName(), new ThemeApiKeyRequest());
            throw new BusinessException(202, "已提交审批，请在「审批中心」查看进度");
        }
        return rotateDirect(consumer);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void revoke(Long themeId) {
        themeService.requireThemeAdmin(themeId);
        Consumer consumer = requireThemeConsumer(themeId);
        if (!authzService.isSuperAdmin()) {
            approvalService.submit(ApprovalResourceType.THEME_API_KEY, consumer.getId(), ApprovalAction.DELETE,
                    themeId, "放弃主题 API Key: " + consumer.getName(), new ThemeApiKeyRequest());
            throw new BusinessException(202, "已提交审批，请在「审批中心」查看进度");
        }
        revokeDirect(consumer, false);
    }

    public ConsumerCreateResponse claimPickup(Long themeId) {
        String rawKey = pickupService.claim(themeId);
        Consumer consumer = requireThemeConsumer(themeId);
        return ConsumerCreateResponse.builder()
                .consumer(toResponse(consumer, themeId))
                .apiKey(rawKey)
                .build();
    }

    /** 审批通过后应用变更；CREATE/ROTATE 返回新 Key 明文（仅一次）。 */
    @Transactional
    public Optional<String> apply(ApprovalAction action, Long themeId, Long resourceId, Object payload) {
        themeRepository.findById(themeId).orElseThrow(() -> new BusinessException("主题不存在"));
        return switch (action) {
            case CREATE -> {
                ThemeApiKeyRequest req = convert(payload, ThemeApiKeyRequest.class);
                if (consumerRepository.existsByThemeId(themeId)) {
                    throw new BusinessException("该主题已有 API Key");
                }
                yield Optional.of(createDirect(themeId, req, true).getApiKey());
            }
            case UPDATE -> {
                ThemeApiKeyRequest req = convert(payload, ThemeApiKeyRequest.class);
                Consumer consumer = requireConsumer(resourceId, themeId);
                updateDirect(consumer, req);
                yield Optional.empty();
            }
            case ROTATE_KEY -> {
                Consumer consumer = requireConsumer(resourceId, themeId);
                yield Optional.of(rotateDirect(consumer, true).getApiKey());
            }
            case DELETE -> {
                Consumer consumer = requireConsumer(resourceId, themeId);
                revokeDirect(consumer, true);
                pickupService.clear(themeId);
                yield Optional.empty();
            }
            default -> throw new BusinessException("不支持的主题 API Key 操作: " + action);
        };
    }

    @Transactional
    public ConsumerCreateResponse createDirect(Long themeId, ThemeApiKeyRequest req) {
        return createDirect(themeId, req, false);
    }

    @Transactional
    public ConsumerCreateResponse createDirect(Long themeId, ThemeApiKeyRequest req, boolean viaApproval) {
        if (consumerRepository.existsByThemeId(themeId)) {
            throw new BusinessException("该主题已有 API Key");
        }
        String rawKey = ApiKeySupport.generateRawKey();
        Consumer consumer = new Consumer();
        consumer.setThemeId(themeId);
        consumer.setName(req.getName().trim());
        consumer.setDepartment(req.getDepartment());
        consumer.setStatus(normalizeStatus(req.getStatus()));
        applyKey(consumer, rawKey);
        Consumer saved = consumerRepository.save(consumer);
        auditKey("CREATE", saved, themeId, viaApproval);
        return ConsumerCreateResponse.builder()
                .consumer(toResponse(saved, themeId))
                .apiKey(rawKey)
                .build();
    }

    @Transactional
    public ConsumerResponse updateDirect(Consumer consumer, ThemeApiKeyRequest req) {
        consumer.setName(req.getName().trim());
        consumer.setDepartment(req.getDepartment());
        consumer.setStatus(normalizeStatus(req.getStatus()));
        Consumer saved = consumerRepository.save(consumer);
        ConsumerResponse response = toResponse(saved, saved.getThemeId());
        auditLogService.log("UPDATE", "THEME_API_KEY", String.valueOf(saved.getId()),
                saved.getThemeId() + ":" + saved.getName(), response);
        return response;
    }

    @Transactional
    public ConsumerCreateResponse rotateDirect(Consumer consumer) {
        return rotateDirect(consumer, false);
    }

    @Transactional
    public ConsumerCreateResponse rotateDirect(Consumer consumer, boolean viaApproval) {
        String rawKey = ApiKeySupport.generateRawKey();
        applyKey(consumer, rawKey);
        Consumer saved = consumerRepository.save(consumer);
        auditKey("ROTATE_KEY", saved, saved.getThemeId(), viaApproval);
        return ConsumerCreateResponse.builder()
                .consumer(toResponse(saved, saved.getThemeId()))
                .apiKey(rawKey)
                .build();
    }

    @Transactional
    public void revokeDirect(Consumer consumer, boolean viaApproval) {
        Long themeId = consumer.getThemeId();
        pickupService.clear(themeId);
        consumerRepository.delete(consumer);
        auditLogService.log("DELETE", "THEME_API_KEY", String.valueOf(consumer.getId()),
                themeId + ":" + consumer.getName(), Map.of("viaApproval", viaApproval));
    }

    private void auditKey(String action, Consumer saved, Long themeId, boolean viaApproval) {
        auditLogService.log(action, "THEME_API_KEY", String.valueOf(saved.getId()),
                themeId + ":" + saved.getName(), Map.of("viaApproval", viaApproval));
    }

    public boolean canAccessThemeKey(Long consumerId, Long apiId) {
        Consumer consumer = consumerRepository.findById(consumerId).orElse(null);
        if (consumer == null || consumer.getThemeId() == null) {
            return false;
        }
        return apiDefinitionRepository.findById(apiId)
                .map(def -> consumer.getThemeId().equals(def.getThemeId()))
                .orElse(false);
    }

    private Consumer requireThemeConsumer(Long themeId) {
        return consumerRepository.findByThemeId(themeId)
                .orElseThrow(() -> new BusinessException("该主题尚未创建 API Key"));
    }

    private Consumer requireConsumer(Long resourceId, Long themeId) {
        Consumer consumer = consumerRepository.findById(resourceId)
                .orElseThrow(() -> new BusinessException("调用方不存在"));
        if (!themeId.equals(consumer.getThemeId())) {
            throw new BusinessException("API Key 与主题不匹配");
        }
        return consumer;
    }

    private void applyKey(Consumer consumer, String rawKey) {
        consumer.setApiKeyHash(ApiKeySupport.hashKey(rawKey, keyProperties.getKeyPepper()));
        consumer.setKeyPrefix(ApiKeySupport.keyPrefix(rawKey));
    }

    private String normalizeStatus(String status) {
        return "DISABLED".equals(status) ? "DISABLED" : "ACTIVE";
    }

    private ConsumerResponse toResponse(Consumer consumer, Long themeId) {
        String themeName = themeRepository.findById(themeId).map(Theme::getName).orElse(null);
        return ConsumerResponse.builder()
                .id(consumer.getId())
                .name(consumer.getName())
                .department(consumer.getDepartment())
                .themeId(themeId)
                .themeName(themeName)
                .keyPrefix(consumer.getKeyPrefix())
                .status(consumer.getStatus())
                .createdAt(consumer.getCreatedAt())
                .build();
    }

    private <T> T convert(Object payload, Class<T> type) {
        return objectMapper.convertValue(payload, type);
    }
}
