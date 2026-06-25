package com.apigateway.repository;

import com.apigateway.entity.ApiAccessLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ApiAccessLogRepository extends JpaRepository<ApiAccessLog, Long> {

    Page<ApiAccessLog> findByApiCodeContainingIgnoreCase(String apiCode, Pageable pageable);

    long countByCreatedAtAfter(LocalDateTime since);

    @Query("SELECT a.status, COUNT(a) FROM ApiAccessLog a WHERE a.createdAt >= :since GROUP BY a.status")
    List<Object[]> countGroupByStatusSince(@Param("since") LocalDateTime since);

    @Query("SELECT a.apiCode, COUNT(a) FROM ApiAccessLog a WHERE a.createdAt >= :since GROUP BY a.apiCode ORDER BY COUNT(a) DESC")
    List<Object[]> topApisSince(@Param("since") LocalDateTime since, Pageable pageable);

    @Query("SELECT COALESCE(AVG(a.durationMs), 0) FROM ApiAccessLog a WHERE a.createdAt >= :since AND a.status = 'SUCCESS'")
    double avgDurationSince(@Param("since") LocalDateTime since);

    @Query("SELECT FUNCTION('date_trunc', 'hour', a.createdAt), COUNT(a) FROM ApiAccessLog a WHERE a.createdAt >= :since GROUP BY FUNCTION('date_trunc', 'hour', a.createdAt) ORDER BY FUNCTION('date_trunc', 'hour', a.createdAt)")
    List<Object[]> hourlyCountsSince(@Param("since") LocalDateTime since);

    @Modifying
    @Query("delete from ApiAccessLog a where a.createdAt < :cutoff")
    long deleteByCreatedAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
