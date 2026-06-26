package com.apigateway.service;

import com.apigateway.config.ConsumerKeyProperties;
import com.apigateway.dto.ConsumerResponse;
import com.apigateway.entity.Consumer;
import com.apigateway.exception.BusinessException;
import com.apigateway.repository.ApiAccessLogRepository;
import com.apigateway.repository.ApiDefinitionRepository;
import com.apigateway.repository.ConsumerApiGrantRepository;
import com.apigateway.repository.ConsumerRepository;
import com.apigateway.repository.ThemeRepository;
import com.apigateway.security.AuthzService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ConsumerService {

    private final ConsumerRepository consumerRepository;
    private final ConsumerApiGrantRepository grantRepository;
    private final ApiDefinitionRepository apiDefinitionRepository;
    private final ThemeRepository themeRepository;
    private final AuthzService authzService;
    private final ThemeApiKeyService themeApiKeyService;
    private final ApiAccessLogRepository accessLogRepository;
    private final ConsumerKeyProperties consumerKeyProperties;

    public Map<String, Object> legacyMigrationStats(int hours) {
        authzService.requireSuperAdmin();
        int effectiveHours = Math.min(Math.max(hours, 1), 168);
        LocalDateTime since = LocalDateTime.now().minusHours(effectiveHours);
        long legacyCalls = accessLogRepository.countByCreatedAtAfterAndAuthMode(since, "LEGACY");
        long themeCalls = accessLogRepository.countByCreatedAtAfterAndAuthMode(since, "THEME_KEY");
        long total = legacyCalls + themeCalls;
        long legacyConsumers = consumerRepository.findAll().stream().filter(c -> c.getThemeId() == null).count();
        long themeConsumers = consumerRepository.findAll().stream().filter(c -> c.getThemeId() != null).count();
        Map<String, Object> stats = new HashMap<>();
        stats.put("hours", effectiveHours);
        stats.put("legacyKeyCalls", legacyCalls);
        stats.put("themeKeyCalls", themeCalls);
        stats.put("legacyKeyPercent", total > 0 ? Math.round(legacyCalls * 1000.0 / total) / 10.0 : 0);
        stats.put("legacyConsumerCount", legacyConsumers);
        stats.put("themeKeyCount", themeConsumers);
        stats.put("legacyEnabled", consumerKeyProperties.isLegacyEnabled());
        stats.put("legacySunsetDate", consumerKeyProperties.getLegacySunsetDate());
        return stats;
    }

    public List<ConsumerResponse> list() {
        authzService.requireSuperAdmin();
        return consumerRepository.findAll().stream()
                .filter(c -> c.getThemeId() != null)
                .map(this::toResponse)
                .toList();
    }

    public ConsumerResponse get(Long id) {
        authzService.requireSuperAdmin();
        return toResponse(findConsumer(id));
    }

    @Transactional
    public void delete(Long id) {
        authzService.requireSuperAdmin();
        Consumer consumer = findConsumer(id);
        if (consumer.getThemeId() != null) {
            throw new BusinessException("主题 API Key 请在主题管理中操作");
        }
        consumerRepository.delete(consumer);
    }

    public Optional<Consumer> authenticate(String rawKey) {
        return themeApiKeyService.authenticate(rawKey);
    }

    public boolean canAccess(Long consumerId, Long apiId) {
        if (themeApiKeyService.canAccessThemeKey(consumerId, apiId)) {
            return true;
        }
        return grantRepository.existsByConsumerIdAndApiId(consumerId, apiId);
    }

    /** 开发环境 bootstrap：为无主题绑定的 legacy 调用方授权全部 API。 */
    @Transactional
    public void grantAllApis(Long consumerId) {
        grantRepository.deleteByConsumerId(consumerId);
        for (var def : apiDefinitionRepository.findAll()) {
            var grant = new com.apigateway.entity.ConsumerApiGrant();
            grant.setConsumerId(consumerId);
            grant.setApiId(def.getId());
            grantRepository.save(grant);
        }
    }

    private Consumer findConsumer(Long id) {
        return consumerRepository.findById(id).orElseThrow(() -> new BusinessException("调用方不存在"));
    }

    private ConsumerResponse toResponse(Consumer consumer) {
        String themeName = consumer.getThemeId() != null
                ? themeRepository.findById(consumer.getThemeId()).map(t -> t.getName()).orElse(null)
                : null;
        return ConsumerResponse.builder()
                .id(consumer.getId())
                .name(consumer.getName())
                .department(consumer.getDepartment())
                .themeId(consumer.getThemeId())
                .themeName(themeName)
                .keyPrefix(consumer.getKeyPrefix())
                .status(consumer.getStatus())
                .apiIds(consumer.getThemeId() == null
                        ? grantRepository.findApiIdsByConsumerId(consumer.getId())
                        : null)
                .createdAt(consumer.getCreatedAt())
                .build();
    }
}
