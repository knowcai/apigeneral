package com.apigateway.repository;

import com.apigateway.entity.ApiDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApiDefinitionRepository extends JpaRepository<ApiDefinition, Long> {

    Optional<ApiDefinition> findByApiCode(String apiCode);

    List<ApiDefinition> findByThemeIdIn(List<Long> themeIds);

    long countByThemeId(Long themeId);

    List<ApiDefinition> findByThemeId(Long themeId);
}
