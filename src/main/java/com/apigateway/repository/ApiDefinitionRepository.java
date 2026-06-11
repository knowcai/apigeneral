package com.apigateway.repository;

import com.apigateway.entity.ApiDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApiDefinitionRepository extends JpaRepository<ApiDefinition, Long> {

    Optional<ApiDefinition> findByApiCode(String apiCode);
}
