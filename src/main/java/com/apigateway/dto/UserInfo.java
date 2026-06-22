package com.apigateway.dto;

import com.apigateway.entity.UserRole;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserInfo {

    private Long id;
    private String username;
    private String displayName;
    private UserRole role;
    private Boolean enabled;

    public boolean superAdmin() {
        return role == UserRole.SUPER_ADMIN;
    }

    public boolean apiEditor() {
        return role == UserRole.API_EDITOR;
    }

    public boolean apiViewer() {
        return role == UserRole.API_VIEWER;
    }

    public boolean canEditPolicy() {
        return superAdmin();
    }

    public boolean canEditDatasource() {
        return superAdmin();
    }

    public boolean canManageUsers() {
        return superAdmin();
    }

    public boolean canEditApi() {
        return superAdmin() || apiEditor();
    }
}
