package com.apigateway.service;

import com.apigateway.dto.ConsumerCreateResponse;
import com.apigateway.dto.ConsumerRequest;
import com.apigateway.dto.ConsumerResponse;
import com.apigateway.entity.ApiDefinition;
import com.apigateway.entity.Consumer;
import com.apigateway.entity.ConsumerApiGrant;
import com.apigateway.exception.BusinessException;
import com.apigateway.repository.ApiDefinitionRepository;
import com.apigateway.repository.ConsumerApiGrantRepository;
import com.apigateway.repository.ConsumerRepository;
import com.apigateway.security.ApiKeySupport;
import com.apigateway.security.AuthzService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ConsumerService {

    private final ConsumerRepository consumerRepository;
    private final ConsumerApiGrantRepository grantRepository;
    private final ApiDefinitionRepository apiDefinitionRepository;
    private final AuthzService authzService;
    private final AuditLogService auditLogService;

    public List<ConsumerResponse> list() {
        authzService.requireSuperAdmin();
        return consumerRepository.findAll().stream().map(this::toResponse).toList();
    }

    public ConsumerResponse get(Long id) {
        authzService.requireSuperAdmin();
        return toResponse(findConsumer(id));
    }

    @Transactional
    public ConsumerCreateResponse create(ConsumerRequest req) {
        authzService.requireSuperAdmin();
        if (consumerRepository.existsByName(req.getName().trim())) {
            throw new BusinessException("调用方名称已存在");
        }
        validateApiIds(req.getApiIds());
        String rawKey = ApiKeySupport.generateRawKey();
        Consumer consumer = new Consumer();
        consumer.setName(req.getName().trim());
        consumer.setDepartment(req.getDepartment());
        consumer.setStatus(normalizeStatus(req.getStatus()));
        applyKey(consumer, rawKey);
        Consumer saved = consumerRepository.save(consumer);
        saveGrants(saved.getId(), req.getApiIds());
        auditLogService.log("CREATE", "CONSUMER", String.valueOf(saved.getId()), saved.getName(), toResponse(saved));
        return ConsumerCreateResponse.builder()
                .consumer(toResponse(saved))
                .apiKey(rawKey)
                .build();
    }

    @Transactional
    public ConsumerResponse update(Long id, ConsumerRequest req) {
        authzService.requireSuperAdmin();
        Consumer consumer = findConsumer(id);
        if (!consumer.getName().equals(req.getName().trim()) && consumerRepository.existsByName(req.getName().trim())) {
            throw new BusinessException("调用方名称已存在");
        }
        validateApiIds(req.getApiIds());
        consumer.setName(req.getName().trim());
        consumer.setDepartment(req.getDepartment());
        consumer.setStatus(normalizeStatus(req.getStatus()));
        Consumer saved = consumerRepository.save(consumer);
        grantRepository.deleteByConsumerId(saved.getId());
        saveGrants(saved.getId(), req.getApiIds());
        ConsumerResponse response = toResponse(saved);
        auditLogService.log("UPDATE", "CONSUMER", String.valueOf(saved.getId()), saved.getName(), response);
        return response;
    }

    @Transactional
    public ConsumerCreateResponse rotateKey(Long id) {
        authzService.requireSuperAdmin();
        Consumer consumer = findConsumer(id);
        String rawKey = ApiKeySupport.generateRawKey();
        applyKey(consumer, rawKey);
        Consumer saved = consumerRepository.save(consumer);
        auditLogService.log("ROTATE_KEY", "CONSUMER", String.valueOf(saved.getId()), saved.getName(), null);
        return ConsumerCreateResponse.builder()
                .consumer(toResponse(saved))
                .apiKey(rawKey)
                .build();
    }

    @Transactional
    public void delete(Long id) {
        authzService.requireSuperAdmin();
        Consumer consumer = findConsumer(id);
        consumerRepository.delete(consumer);
        auditLogService.log("DELETE", "CONSUMER", String.valueOf(id), consumer.getName(), null);
    }

    public Optional<Consumer> authenticate(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return Optional.empty();
        }
        return consumerRepository.findByApiKeyHash(ApiKeySupport.hashKey(rawKey.trim()))
                .filter(c -> "ACTIVE".equals(c.getStatus()));
    }

    public boolean canAccess(Long consumerId, Long apiId) {
        return grantRepository.existsByConsumerIdAndApiId(consumerId, apiId);
    }

    @Transactional
    public void grantAllApis(Long consumerId) {
        grantRepository.deleteByConsumerId(consumerId);
        for (ApiDefinition def : apiDefinitionRepository.findAll()) {
            ConsumerApiGrant grant = new ConsumerApiGrant();
            grant.setConsumerId(consumerId);
            grant.setApiId(def.getId());
            grantRepository.save(grant);
        }
    }

    private Consumer findConsumer(Long id) {
        return consumerRepository.findById(id).orElseThrow(() -> new BusinessException("调用方不存在"));
    }

    private void validateApiIds(List<Long> apiIds) {
        if (apiIds == null || apiIds.isEmpty()) {
            throw new BusinessException("请至少授权一个 API");
        }
        for (Long apiId : apiIds) {
            if (!apiDefinitionRepository.existsById(apiId)) {
                throw new BusinessException("API 不存在: " + apiId);
            }
        }
    }

    private void saveGrants(Long consumerId, List<Long> apiIds) {
        for (Long apiId : apiIds) {
            ConsumerApiGrant grant = new ConsumerApiGrant();
            grant.setConsumerId(consumerId);
            grant.setApiId(apiId);
            grantRepository.save(grant);
        }
    }

    private void applyKey(Consumer consumer, String rawKey) {
        consumer.setApiKeyHash(ApiKeySupport.hashKey(rawKey));
        consumer.setKeyPrefix(ApiKeySupport.keyPrefix(rawKey));
    }

    private String normalizeStatus(String status) {
        return "DISABLED".equals(status) ? "DISABLED" : "ACTIVE";
    }

    private ConsumerResponse toResponse(Consumer consumer) {
        return ConsumerResponse.builder()
                .id(consumer.getId())
                .name(consumer.getName())
                .department(consumer.getDepartment())
                .keyPrefix(consumer.getKeyPrefix())
                .status(consumer.getStatus())
                .apiIds(grantRepository.findApiIdsByConsumerId(consumer.getId()))
                .createdAt(consumer.getCreatedAt())
                .build();
    }
}
