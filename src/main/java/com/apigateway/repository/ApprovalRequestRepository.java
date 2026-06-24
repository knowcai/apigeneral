package com.apigateway.repository;

import com.apigateway.entity.ApprovalRequest;
import com.apigateway.entity.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

    List<ApprovalRequest> findByStatusOrderByCreatedAtDesc(ApprovalStatus status);

    List<ApprovalRequest> findByThemeIdAndStatusOrderByCreatedAtDesc(Long themeId, ApprovalStatus status);
}
