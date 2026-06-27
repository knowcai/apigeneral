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

    /** 主题 API Key 摘要 */
    private Integer apiKeyUsedSlots;
    private Integer apiKeyMaxSlots;
    /** NONE | ACTIVE */
    private String apiKeyPhase;
    /** 主题下 API 数量（超管删除前校验） */
    private Long apiCount;

    @Data
    @Builder
    public static class Member {
        private Long userId;
        private String username;
        private String displayName;
        private ThemeMembershipRole role;
    }
}
