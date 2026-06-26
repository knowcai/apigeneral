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

    /** 主题 API Key 摘要（仅列表/详情展示） */
    private String apiKeyPrefix;
    private String apiKeyStatus;
    /** NONE | PENDING | ACTIVE | DISABLED */
    private String apiKeyPhase;
    private Long apiKeyPendingRequestId;
    /** CREATE | ROTATE_KEY | DELETE */
    private String apiKeyPendingAction;
    /** 审批通过后完整 Key 待主题管理员领取（先到先得，领取后全员不可再领） */
    private Boolean apiKeyPickupPending;

    @Data
    @Builder
    public static class Member {
        private Long userId;
        private String username;
        private String displayName;
        private ThemeMembershipRole role;
    }
}
