package com.apigateway.security;

import com.apigateway.entity.ApiDefinition;
import com.apigateway.exception.BusinessException;
import com.apigateway.repository.ThemeMembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthzService {

    private final CurrentUser currentUser;
    private final ThemeMembershipRepository membershipRepository;

    public void requireSuperAdmin() {
        if (!currentUser.isSuperAdmin()) {
            throw new BusinessException(403, "需要超级管理员权限");
        }
    }

    public void requireAuthenticated() {
        currentUser.requireUser();
    }

    public void requireNotViewer() {
        requireAuthenticated();
        if (currentUser.isApiViewer()) {
            throw new BusinessException(403, "只读用户无权修改");
        }
    }

    public boolean isSuperAdmin() {
        return currentUser.isSuperAdmin();
    }

    public Long currentUserId() {
        return currentUser.requireUser().getId();
    }

    public void requireApiRead() {
        requireAuthenticated();
    }

    public void requireApiWrite(ApiDefinition def) {
        requireNotViewer();
        if (isSuperAdmin()) {
            return;
        }
        if (def.getThemeId() == null) {
            throw new BusinessException(403, "该 API 未绑定主题");
        }
        membershipRepository.findByThemeIdAndUserId(def.getThemeId(), currentUserId())
                .orElseThrow(() -> new BusinessException(403, "无权操作该主题下的 API"));
    }

    public void requireApiCreate() {
        requireNotViewer();
        if (isSuperAdmin()) {
            return;
        }
        if (membershipRepository.findByUserId(currentUserId()).isEmpty()) {
            throw new BusinessException(403, "无权新建 API，请联系管理员分配主题");
        }
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
