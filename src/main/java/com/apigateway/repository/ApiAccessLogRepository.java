package com.apigateway.repository;

import com.apigateway.entity.ApiAccessLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ApiAccessLogRepository extends JpaRepository<ApiAccessLog, Long>, JpaSpecificationExecutor<ApiAccessLog> {

    Page<ApiAccessLog> findByApiCodeContainingIgnoreCase(String apiCode, Pageable pageable);

    long countByCreatedAtAfter(LocalDateTime since);

    @Query("SELECT a.status, COUNT(a) FROM ApiAccessLog a WHERE a.createdAt >= :since GROUP BY a.status")
    List<Object[]> countGroupByStatusSince(@Param("since") LocalDateTime since);

    @Query("SELECT a.apiCode, COUNT(a) FROM ApiAccessLog a WHERE a.createdAt >= :since GROUP BY a.apiCode ORDER BY COUNT(a) DESC")
    List<Object[]> topApisSince(@Param("since") LocalDateTime since, Pageable pageable);

    @Query("SELECT a.apiCode, COUNT(a) FROM ApiAccessLog a WHERE a.createdAt >= :since AND a.status = :status GROUP BY a.apiCode ORDER BY COUNT(a) DESC")
    List<Object[]> topApisByStatusSince(@Param("since") LocalDateTime since, @Param("status") String status, Pageable pageable);

    @Query("SELECT COALESCE(AVG(a.durationMs), 0) FROM ApiAccessLog a WHERE a.createdAt >= :since AND a.status = 'SUCCESS'")
    double avgDurationSince(@Param("since") LocalDateTime since);

    @Query("SELECT FUNCTION('date_trunc', 'hour', a.createdAt), COUNT(a) FROM ApiAccessLog a WHERE a.createdAt >= :since GROUP BY FUNCTION('date_trunc', 'hour', a.createdAt) ORDER BY FUNCTION('date_trunc', 'hour', a.createdAt)")
    List<Object[]> hourlyCountsSince(@Param("since") LocalDateTime since);

    @Modifying
    @Query("delete from ApiAccessLog a where a.createdAt < :cutoff")
    long deleteByCreatedAtBefore(@Param("cutoff") LocalDateTime cutoff);

    long countByCreatedAtAfterAndApiCodeIn(LocalDateTime since, List<String> apiCodes);

    long countByCreatedAtAfterAndApiCodeInAndAuthMode(LocalDateTime since, List<String> apiCodes, String authMode);

    long countByCreatedAtAfterAndAuthMode(LocalDateTime since, String authMode);

    @Query("SELECT a.status, COUNT(a) FROM ApiAccessLog a WHERE a.createdAt >= :since AND a.apiCode IN :apiCodes GROUP BY a.status")
    List<Object[]> countGroupByStatusSinceAndApiCodeIn(@Param("since") LocalDateTime since, @Param("apiCodes") List<String> apiCodes);

    @Query("SELECT a.apiCode, COUNT(a) FROM ApiAccessLog a WHERE a.createdAt >= :since AND a.apiCode IN :apiCodes GROUP BY a.apiCode ORDER BY COUNT(a) DESC")
    List<Object[]> topApisSinceAndApiCodeIn(@Param("since") LocalDateTime since, @Param("apiCodes") List<String> apiCodes, Pageable pageable);

    @Query("SELECT a.apiCode, COUNT(a) FROM ApiAccessLog a WHERE a.createdAt >= :since AND a.apiCode IN :apiCodes AND a.status = :status GROUP BY a.apiCode ORDER BY COUNT(a) DESC")
    List<Object[]> topApisByStatusSinceAndApiCodeIn(@Param("since") LocalDateTime since, @Param("apiCodes") List<String> apiCodes, @Param("status") String status, Pageable pageable);

    @Query("SELECT COALESCE(AVG(a.durationMs), 0) FROM ApiAccessLog a WHERE a.createdAt >= :since AND a.status = 'SUCCESS' AND a.apiCode IN :apiCodes")
    double avgDurationSinceAndApiCodeIn(@Param("since") LocalDateTime since, @Param("apiCodes") List<String> apiCodes);

    @Query("SELECT FUNCTION('date_trunc', 'hour', a.createdAt), COUNT(a) FROM ApiAccessLog a WHERE a.createdAt >= :since AND a.apiCode IN :apiCodes GROUP BY FUNCTION('date_trunc', 'hour', a.createdAt) ORDER BY FUNCTION('date_trunc', 'hour', a.createdAt)")
    List<Object[]> hourlyCountsSinceAndApiCodeIn(@Param("since") LocalDateTime since, @Param("apiCodes") List<String> apiCodes);

    @Query("""
            SELECT a.apiCode, COALESCE(a.consumerName, '—'), a.consumerId, COUNT(a)
            FROM ApiAccessLog a
            WHERE a.createdAt >= :since AND a.consumerId IS NOT NULL
            GROUP BY a.apiCode, a.consumerName, a.consumerId
            ORDER BY COUNT(a) DESC
            """)
    List<Object[]> apiKeyUsageSince(@Param("since") LocalDateTime since, Pageable pageable);

    @Query("""
            SELECT a.apiCode, COALESCE(a.consumerName, '—'), a.consumerId, COUNT(a)
            FROM ApiAccessLog a
            WHERE a.createdAt >= :since AND a.consumerId IS NOT NULL AND a.apiCode IN :apiCodes
            GROUP BY a.apiCode, a.consumerName, a.consumerId
            ORDER BY COUNT(a) DESC
            """)
    List<Object[]> apiKeyUsageSinceAndApiCodeIn(@Param("since") LocalDateTime since, @Param("apiCodes") List<String> apiCodes, Pageable pageable);
}
