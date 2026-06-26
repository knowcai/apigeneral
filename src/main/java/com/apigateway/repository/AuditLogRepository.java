package com.apigateway.repository;

import com.apigateway.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<AuditLog> findByResourceTypeAndActionInOrderByCreatedAtDesc(
            String resourceType, List<String> actions, Pageable pageable);
}
