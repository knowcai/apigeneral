package com.apigateway.repository;

import com.apigateway.entity.ThemeApiKeyPickup;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ThemeApiKeyPickupRepository extends JpaRepository<ThemeApiKeyPickup, Long> {

    boolean existsByConsumerId(Long consumerId);

    void deleteByConsumerId(Long consumerId);

    void deleteByThemeId(Long themeId);

    List<ThemeApiKeyPickup> findByThemeId(Long themeId);

    List<ThemeApiKeyPickup> findByConsumerIdIn(Collection<Long> consumerIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ThemeApiKeyPickup p WHERE p.consumerId = :consumerId")
    Optional<ThemeApiKeyPickup> findByConsumerIdForUpdate(@Param("consumerId") Long consumerId);
}
