package com.apigateway.repository;

import com.apigateway.entity.Consumer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConsumerRepository extends JpaRepository<Consumer, Long> {

    Optional<Consumer> findByApiKeyHash(String apiKeyHash);

    Optional<Consumer> findByThemeId(Long themeId);

    boolean existsByThemeId(Long themeId);

    boolean existsByName(String name);
}
