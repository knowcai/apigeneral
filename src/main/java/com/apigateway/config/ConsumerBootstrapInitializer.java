package com.apigateway.config;

import com.apigateway.config.ConsumerKeyProperties;
import com.apigateway.entity.Consumer;
import com.apigateway.repository.ConsumerRepository;
import com.apigateway.security.ApiKeySupport;
import com.apigateway.service.ConsumerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
@Slf4j
@Component
@RequiredArgsConstructor
public class ConsumerBootstrapInitializer implements ApplicationRunner {

    private final ConsumerRepository consumerRepository;
    private final ConsumerService consumerService;
    private final ConsumerKeyProperties keyProperties;

    @Value("${gateway.consumer.bootstrap-key:}")
    private String bootstrapKey;

    @Value("${gateway.consumer.bootstrap-name:开发默认调用方}")
    private String bootstrapName;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (bootstrapKey == null || bootstrapKey.isBlank()) {
            return;
        }
        String trimmed = bootstrapKey.trim();
        String hash = ApiKeySupport.hashKey(trimmed, keyProperties.getKeyPepper());
        Optional<Consumer> existing = consumerRepository.findByApiKeyHash(hash);
        if (existing.isEmpty()) {
            existing = consumerRepository.findByApiKeyHash(ApiKeySupport.legacyHashKey(trimmed));
        }
        if (existing.isPresent()) {
            consumerService.grantAllApis(existing.get().getId());
            return;
        }
        Consumer consumer = new Consumer();
        consumer.setName(bootstrapName);
        consumer.setDepartment("dev");
        consumer.setStatus("ACTIVE");
        consumer.setApiKeyHash(hash);
        consumer.setKeyPrefix(ApiKeySupport.keyPrefix(trimmed));
        consumer = consumerRepository.save(consumer);
        consumerService.grantAllApis(consumer.getId());
        log.warn("已创建开发用默认调用方「{}」，API Key 见 gateway.consumer.bootstrap-key 配置", bootstrapName);
    }
}
