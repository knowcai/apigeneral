package com.apigateway.service;

import com.apigateway.entity.ApprovalAction;
import com.apigateway.entity.ThemeApiKeyPickup;
import com.apigateway.exception.BusinessException;
import com.apigateway.repository.ThemeApiKeyPickupRepository;
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
    private final AuditLogService auditLogService;

    public boolean hasPendingPickup(Long themeId) {
        return pickupRepository.existsByThemeId(themeId);
    }

    @Transactional
    public void store(Long themeId, Long approvalRequestId, ApprovalAction action, String rawKey) {
        pickupRepository.deleteByThemeId(themeId);
        var pickup = new ThemeApiKeyPickup();
        pickup.setThemeId(themeId);
        pickup.setApprovalRequestId(approvalRequestId);
        pickup.setAction(action.name());
        pickup.setEncryptedKey(cryptoService.encrypt(rawKey));
        pickupRepository.save(pickup);
        auditLogService.log("STORE_PICKUP", "THEME_API_KEY", String.valueOf(themeId),
                themeId + ":" + action.name(), null);
    }

    @Transactional
    public void clear(Long themeId) {
        if (pickupRepository.existsByThemeId(themeId)) {
            pickupRepository.deleteByThemeId(themeId);
            auditLogService.log("CLEAR_PICKUP", "THEME_API_KEY", String.valueOf(themeId), String.valueOf(themeId), null);
        }
    }

    @Transactional
    public String claim(Long themeId) {
        themeService.requireThemeAdmin(themeId);
        var pickup = pickupRepository.findByThemeIdForUpdate(themeId)
                .orElseThrow(() -> new BusinessException("没有待领取的 API Key"));
        String rawKey = cryptoService.decrypt(pickup.getEncryptedKey());
        pickupRepository.delete(pickup);
        auditLogService.log("CLAIM_PICKUP", "THEME_API_KEY", String.valueOf(themeId), themeId + ":claimed", null);
        return rawKey;
    }
}
