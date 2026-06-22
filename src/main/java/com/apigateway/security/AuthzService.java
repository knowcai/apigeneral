package com.apigateway.security;

import com.apigateway.entity.ApiDefinition;
import com.apigateway.entity.UserRole;
import com.apigateway.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthzService {

    private final CurrentUser currentUser;

    public void requireSuperAdmin() {
        if (!currentUser.isSuperAdmin()) {
            throw new BusinessException(403, "需要超级管理员权限");
        }
    }

    public void requireAuthenticated() {
        currentUser.requireUser();
    }

    public void requireApiRead() {
        requireAuthenticated();
    }

    public void requireApiWrite(ApiDefinition def) {
        AuthUser user = currentUser.requireUser();
        if (user.getRole() == UserRole.SUPER_ADMIN) {
            return;
        }
        if (user.getRole() == UserRole.API_EDITOR && def.getCreatedBy() != null
                && def.getCreatedBy().equals(user.getUsername())) {
            return;
        }
        throw new BusinessException(403, "无权修改该 API，仅创建人或超级管理员可操作");
    }

    public void requireApiCreate() {
        AuthUser user = currentUser.requireUser();
        if (user.getRole() == UserRole.SUPER_ADMIN || user.getRole() == UserRole.API_EDITOR) {
            return;
        }
        throw new BusinessException(403, "无权新建 API");
    }

    public boolean canWriteApi(ApiDefinition def) {
        try {
            requireApiWrite(def);
            return true;
        } catch (BusinessException e) {
            return false;
        }
    }
}
