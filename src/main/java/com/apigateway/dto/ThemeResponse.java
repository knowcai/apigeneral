package com.apigateway.dto;

import com.apigateway.entity.ThemeMembershipRole;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ThemeResponse {

    private Long id;
    private String code;
    private String name;
    private String description;
    private Boolean enabled;
    private List<Member> members;
    private LocalDateTime createdAt;

    /** 当前登录用户在该主题下的角色，超级管理员为 null */
    private ThemeMembershipRole myRole;

    @Data
    @Builder
    public static class Member {
        private Long userId;
        private String username;
        private String displayName;
        private ThemeMembershipRole role;
    }
}
