package com.apigateway.repository;

import com.apigateway.entity.ApprovalResourceType;
import com.apigateway.entity.ApprovalRequest;
import com.apigateway.entity.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

    List<ApprovalRequest> findByStatusOrderByCreatedAtDesc(ApprovalStatus status);

    List<ApprovalRequest> findByThemeIdAndStatusOrderByCreatedAtDesc(Long themeId, ApprovalStatus status);

    Page<ApprovalRequest> findByStatusInOrderByResolvedAtDesc(Collection<ApprovalStatus> statuses, Pageable pageable);

    boolean existsByResourceTypeAndResourceIdAndStatus(ApprovalResourceType resourceType, Long resourceId, ApprovalStatus status);

    List<ApprovalRequest> findByThemeIdAndResourceTypeAndStatus(Long themeId, ApprovalResourceType resourceType, ApprovalStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ApprovalRequest r WHERE r.id = :id")
    Optional<ApprovalRequest> findByIdForUpdate(@Param("id") Long id);
}
