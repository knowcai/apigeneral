package com.apigateway.repository;

import com.apigateway.entity.ThemeMembership;
import com.apigateway.entity.ThemeMembershipRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ThemeMembershipRepository extends JpaRepository<ThemeMembership, ThemeMembership.Pk> {

    List<ThemeMembership> findByUserId(Long userId);

    List<ThemeMembership> findByThemeId(Long themeId);

    long countByThemeIdAndRole(Long themeId, ThemeMembershipRole role);

    Optional<ThemeMembership> findByThemeIdAndUserId(Long themeId, Long userId);
}
