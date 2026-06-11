package com.apigateway.repository;

import com.apigateway.entity.ApiAccessLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiAccessLogRepository extends JpaRepository<ApiAccessLog, Long> {

    Page<ApiAccessLog> findByApiCodeContainingIgnoreCase(String apiCode, Pageable pageable);
}
