package com.apigateway.security;

import com.apigateway.entity.SysUser;
import com.apigateway.entity.UserRole;
import com.apigateway.exception.BusinessException;
import com.apigateway.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CurrentUser {

    private final SysUserRepository userRepository;

    public AuthUser requireUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser user)) {
            throw new BusinessException(401, "未登录");
        }
        return user;
    }

    public String username() {
        return requireUser().getUsername();
    }

    public UserRole role() {
        return requireUser().getRole();
    }

    public boolean isSuperAdmin() {
        return role() == UserRole.SUPER_ADMIN;
    }

    public boolean isApiEditor() {
        return role() == UserRole.API_EDITOR;
    }

    public boolean isApiViewer() {
        return role() == UserRole.API_VIEWER;
    }

    public SysUser requireEntity() {
        AuthUser user = requireUser();
        return userRepository.findById(user.getId())
                .orElseThrow(() -> new BusinessException(401, "用户不存在"));
    }
}
