package com.apigateway.service;

import com.apigateway.entity.ApprovalAction;
import com.apigateway.entity.ThemeApiKeyPickup;
import com.apigateway.exception.BusinessException;
import com.apigateway.repository.ThemeApiKeyPickupRepository;
import com.apigateway.security.CurrentUser;
import com.apigateway.security.SecretCryptoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ThemeApiKeyPickupService {

    private final ThemeApiKeyPickupRepository pickupRepository;
    private final SecretCryptoService cryptoService;
    private final ThemeService themeService;
    private final CurrentUser currentUser;
    private final AuditLogService auditLogService;

    public boolean hasPendingPickup(Long consumerId) {
        return pickupRepository.existsByConsumerId(consumerId);
    }

    @Transactional
    public void store(Long themeId, Long consumerId, Long submitterId, Long approvalRequestId,
                      ApprovalAction action, String rawKey) {
        pickupRepository.deleteByConsumerId(consumerId);
        var pickup = new ThemeApiKeyPickup();
        pickup.setThemeId(themeId);
        pickup.setConsumerId(consumerId);
        pickup.setSubmitterId(submitterId);
        pickup.setApprovalRequestId(approvalRequestId);
        pickup.setAction(action.name());
        pickup.setEncryptedKey(cryptoService.encrypt(rawKey));
        pickupRepository.save(pickup);
        auditLogService.log("STORE_PICKUP", "THEME_API_KEY", String.valueOf(consumerId),
                themeId + ":" + action.name(), null);
    }

    @Transactional
    public void clear(Long consumerId) {
        if (pickupRepository.existsByConsumerId(consumerId)) {
            pickupRepository.deleteByConsumerId(consumerId);
            auditLogService.log("CLEAR_PICKUP", "THEME_API_KEY", String.valueOf(consumerId),
                    String.valueOf(consumerId), null);
        }
    }

    @Transactional
    public void clearTheme(Long themeId) {
        pickupRepository.deleteByThemeId(themeId);
    }

    @Transactional
    public String claim(Long themeId, Long consumerId) {
        themeService.requireThemeAdmin(themeId);
        Long uid = currentUser.requireUser().getId();
        var pickup = pickupRepository.findByConsumerIdForUpdate(consumerId)
                .orElseThrow(() -> new BusinessException("没有待领取的 API Key"));
        if (!themeId.equals(pickup.getThemeId())) {
            throw new BusinessException("API Key 与主题不匹配");
        }
        if (!uid.equals(pickup.getSubmitterId())) {
            throw new BusinessException(403, "仅提交人可领取该 API Key");
        }
        String rawKey = cryptoService.decrypt(pickup.getEncryptedKey());
        pickupRepository.delete(pickup);
        auditLogService.log("CLAIM_PICKUP", "THEME_API_KEY", String.valueOf(consumerId),
                themeId + ":claimed", null);
        return rawKey;
    }
}
