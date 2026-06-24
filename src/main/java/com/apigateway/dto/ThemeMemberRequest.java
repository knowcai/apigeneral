package com.apigateway.dto;

import com.apigateway.entity.ThemeMembershipRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ThemeMemberRequest {

    @NotNull
    private Long userId;

    @NotNull
    private ThemeMembershipRole role;
}
