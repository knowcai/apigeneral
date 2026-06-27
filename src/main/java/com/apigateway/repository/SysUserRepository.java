package com.apigateway.repository;

import com.apigateway.entity.SysUser;
import com.apigateway.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SysUserRepository extends JpaRepository<SysUser, Long> {

    Optional<SysUser> findByUsername(String username);

    long countByRole(UserRole role);

    List<SysUser> findByRoleAndEnabled(UserRole role, Boolean enabled);
}
