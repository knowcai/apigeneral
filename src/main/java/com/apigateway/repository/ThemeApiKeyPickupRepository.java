package com.apigateway.repository;

import com.apigateway.entity.ThemeApiKeyPickup;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ThemeApiKeyPickupRepository extends JpaRepository<ThemeApiKeyPickup, Long> {

    boolean existsByThemeId(Long themeId);

    void deleteByThemeId(Long themeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ThemeApiKeyPickup p WHERE p.themeId = :themeId")
    Optional<ThemeApiKeyPickup> findByThemeIdForUpdate(@Param("themeId") Long themeId);
}
