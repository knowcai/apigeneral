package com.apigateway.repository;

import com.apigateway.entity.GatewayPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GatewayPolicyRepository extends JpaRepository<GatewayPolicy, Long> {
}
