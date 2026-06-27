package com.apigateway.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ThemeApiKeyItemResponse {

    private Long id;
    private String name;
    private String keyPrefix;
    /** ACTIVE | PENDING_CREATE | PENDING_DELETE */
    private String phase;
    private Long pendingRequestId;
    private Boolean pickupPending;
    /** 当前用户是否为可领取的提交人 */
    private Boolean canClaim;
    /** 当前用户是否可撤回该审批 */
    private Boolean canWithdraw;
    private LocalDateTime createdAt;
}
