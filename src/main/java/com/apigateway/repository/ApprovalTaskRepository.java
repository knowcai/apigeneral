package com.apigateway.repository;

import com.apigateway.entity.ApprovalStatus;
import com.apigateway.entity.ApprovalTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalTaskRepository extends JpaRepository<ApprovalTask, Long> {

    List<ApprovalTask> findByRequestIdOrderByStepOrderAscSortOrderAsc(Long requestId);

    List<ApprovalTask> findByAssigneeIdAndStatusOrderByIdDesc(Long assigneeId, ApprovalStatus status);

    List<ApprovalTask> findByStatusOrderByIdDesc(ApprovalStatus status);
}
