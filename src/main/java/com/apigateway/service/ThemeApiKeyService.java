package com.apigateway.service;

import com.apigateway.config.ConsumerKeyProperties;
import com.apigateway.dto.*;
import com.apigateway.entity.*;
import com.apigateway.exception.BusinessException;
import com.apigateway.repository.ApiDefinitionRepository;
import com.apigateway.repository.ApprovalRequestRepository;
import com.apigateway.repository.ConsumerRepository;
import com.apigateway.repository.ThemeApiKeyPickupRepository;
import com.apigateway.repository.ThemeRepository;
import com.apigateway.security.ApiKeySupport;
import com.apigateway.security.AuthzService;
import com.apigateway.security.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ThemeApiKeyService {

    public static final int MAX_KEYS_PER_THEME = 5;

    private final ConsumerRepository consumerRepository;
    private final ThemeRepository themeRepository;
    private final ApiDefinitionRepository apiDefinitionRepository;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final ThemeApiKeyPickupRepository pickupRepository;
    private final ThemeService themeService;
    private final AuthzService authzService;
    private final CurrentUser currentUser;
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

    public ThemeApiKeyListResponse listKeys(Long themeId) {
        themeService.requireThemeRead(themeId);
        Long uid = currentUser.requireUser().getId();
        boolean canManage = themeService.isThemeAdminMember(themeId, uid);

        List<ApprovalRequest> pending = approvalRequestRepository.findByThemeIdAndResourceTypeAndStatus(
                themeId, ApprovalResourceType.THEME_API_KEY, ApprovalStatus.PENDING);

        Map<Long, ApprovalRequest> pendingDeleteByConsumer = new HashMap<>();
        List<ApprovalRequest> pendingCreates = new ArrayList<>();
        for (ApprovalRequest req : pending) {
            if (req.getAction() == ApprovalAction.CREATE) {
                pendingCreates.add(req);
            } else if (req.getAction() == ApprovalAction.DELETE && req.getResourceId() != null) {
                pendingDeleteByConsumer.put(req.getResourceId(), req);
            }
        }

        List<ThemeApiKeyItemResponse> items = new ArrayList<>();
        List<Consumer> consumers = consumerRepository.findAllByThemeIdOrderByCreatedAtAsc(themeId);
        Set<Long> consumerIds = new HashSet<>();
        for (Consumer consumer : consumers) {
            consumerIds.add(consumer.getId());
            items.add(buildItem(themeId, consumer, pendingDeleteByConsumer.get(consumer.getId()), uid, canManage));
        }
        for (ApprovalRequest req : pendingCreates) {
            items.add(buildPendingCreateItem(req, uid, canManage));
        }

        return ThemeApiKeyListResponse.builder()
                .keys(items)
                .usedSlots(countUsedSlots(themeId))
                .maxSlots(MAX_KEYS_PER_THEME)
                .canManage(canManage)
                .build();
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public ConsumerCreateResponse create(Long themeId, ThemeApiKeyRequest req) {
        themeRepository.findById(themeId).orElseThrow(() -> new BusinessException("主题不存在"));
        if (authzService.isSuperAdmin()) {
            throw new BusinessException(403, "主题 API Key 由主题管理员管理，超级管理员不可新建");
        }
        themeService.requireThemeAdmin(themeId);
        ensureSlotAvailable(themeId);
        approvalService.submit(ApprovalResourceType.THEME_API_KEY, null, ApprovalAction.CREATE,
                themeId, "创建主题 API Key: " + req.getName().trim(), req);
        throw new BusinessException(202, "已提交审批，请在「审批中心」查看进度");
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void deleteKey(Long themeId, Long keyId) {
        if (authzService.isSuperAdmin()) {
            throw new BusinessException(403, "主题 API Key 由主题管理员管理，超级管理员不可删除");
        }
        themeService.requireThemeAdmin(themeId);
        Consumer consumer = requireConsumer(keyId, themeId);
        approvalService.submit(ApprovalResourceType.THEME_API_KEY, consumer.getId(), ApprovalAction.DELETE,
                themeId, "删除主题 API Key: " + consumer.getName(), new ThemeApiKeyRequest());
        throw new BusinessException(202, "已提交审批，请在「审批中心」查看进度");
    }

    public ConsumerCreateResponse claimPickup(Long themeId, Long keyId) {
        requireConsumer(keyId, themeId);
        String rawKey = pickupService.claim(themeId, keyId);
        Consumer consumer = requireConsumer(keyId, themeId);
        return ConsumerCreateResponse.builder()
                .consumer(toResponse(consumer, themeId))
                .apiKey(rawKey)
                .build();
    }

    /** 审批通过后应用变更；CREATE 时写入待领取（仅提交人可领）。 */
    @Transactional
    public Optional<String> apply(ApprovalAction action, Long themeId, Long resourceId, Object payload,
                                  Long submitterId, Long approvalRequestId) {
        themeRepository.findById(themeId).orElseThrow(() -> new BusinessException("主题不存在"));
        return switch (action) {
            case CREATE -> {
                ThemeApiKeyRequest req = convert(payload, ThemeApiKeyRequest.class);
                ensureSlotAvailable(themeId);
                String rawKey = ApiKeySupport.generateRawKey();
                Consumer consumer = new Consumer();
                consumer.setThemeId(themeId);
                consumer.setName(req.getName().trim());
                consumer.setDepartment(req.getDepartment());
                consumer.setStatus(normalizeStatus(req.getStatus()));
                applyKey(consumer, rawKey);
                Consumer saved = consumerRepository.save(consumer);
                auditKey("CREATE", saved, themeId, true);
                pickupService.store(themeId, saved.getId(), submitterId, approvalRequestId,
                        ApprovalAction.CREATE, rawKey);
                yield Optional.empty();
            }
            case DELETE -> {
                Consumer consumer = requireConsumer(resourceId, themeId);
                revokeDirect(consumer, true);
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
        ensureSlotAvailable(themeId);
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
                .apiKey(viaApproval ? null : rawKey)
                .build();
    }

    @Transactional
    public void revokeDirect(Consumer consumer, boolean viaApproval) {
        Long themeId = consumer.getThemeId();
        pickupService.clear(consumer.getId());
        consumerRepository.delete(consumer);
        auditLogService.log("DELETE", "THEME_API_KEY", String.valueOf(consumer.getId()),
                themeId + ":" + consumer.getName(), Map.of("viaApproval", viaApproval));
    }

    public int countUsedSlots(Long themeId) {
        long active = consumerRepository.countByThemeId(themeId);
        long pendingCreate = approvalRequestRepository.countByThemeIdAndResourceTypeAndStatusAndAction(
                themeId, ApprovalResourceType.THEME_API_KEY, ApprovalStatus.PENDING, ApprovalAction.CREATE);
        return (int) (active + pendingCreate);
    }

    public void ensureSlotAvailable(Long themeId) {
        if (countUsedSlots(themeId) >= MAX_KEYS_PER_THEME) {
            throw new BusinessException("该主题 API Key 已达上限（" + MAX_KEYS_PER_THEME + " 把，含审批中）");
        }
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

    private ThemeApiKeyItemResponse buildItem(Long themeId, Consumer consumer, ApprovalRequest pendingDelete,
                                              Long uid, boolean canManage) {
        boolean pickupPending = pickupRepository.existsByConsumerId(consumer.getId());
        Optional<ThemeApiKeyPickup> pickup = pickupPending
                ? pickupRepository.findByConsumerIdIn(List.of(consumer.getId())).stream().findFirst()
                : Optional.empty();
        boolean canClaim = pickupPending && pickup.map(p -> uid.equals(p.getSubmitterId())).orElse(false);
        boolean canWithdraw = pendingDelete != null && uid.equals(pendingDelete.getSubmitterId());

        String phase = pendingDelete != null ? "PENDING_DELETE" : "ACTIVE";
        return ThemeApiKeyItemResponse.builder()
                .id(consumer.getId())
                .name(consumer.getName())
                .keyPrefix(consumer.getKeyPrefix())
                .phase(phase)
                .pendingRequestId(pendingDelete != null ? pendingDelete.getId() : null)
                .pickupPending(pickupPending)
                .canClaim(canClaim)
                .canWithdraw(canWithdraw && canManage)
                .createdAt(consumer.getCreatedAt())
                .build();
    }

    private ThemeApiKeyItemResponse buildPendingCreateItem(ApprovalRequest req, Long uid, boolean canManage) {
        String name = req.getTitle();
        try {
            ThemeApiKeyRequest payload = objectMapper.readValue(req.getPayload(), ThemeApiKeyRequest.class);
            if (payload.getName() != null && !payload.getName().isBlank()) {
                name = payload.getName().trim();
            }
        } catch (Exception ignored) {
        }
        boolean canWithdraw = canManage && uid.equals(req.getSubmitterId());
        return ThemeApiKeyItemResponse.builder()
                .id(null)
                .name(name)
                .keyPrefix(null)
                .phase("PENDING_CREATE")
                .pendingRequestId(req.getId())
                .pickupPending(false)
                .canClaim(false)
                .canWithdraw(canWithdraw)
                .createdAt(req.getCreatedAt())
                .build();
    }

    private Consumer requireConsumer(Long resourceId, Long themeId) {
        Consumer consumer = consumerRepository.findById(resourceId)
                .orElseThrow(() -> new BusinessException("API Key 不存在"));
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

    private void auditKey(String action, Consumer saved, Long themeId, boolean viaApproval) {
        auditLogService.log(action, "THEME_API_KEY", String.valueOf(saved.getId()),
                themeId + ":" + saved.getName(), Map.of("viaApproval", viaApproval));
    }

    private <T> T convert(Object payload, Class<T> type) {
        return objectMapper.convertValue(payload, type);
    }
}
