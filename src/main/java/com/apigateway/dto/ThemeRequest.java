package com.apigateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ThemeRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    private String description;
    private Boolean enabled = true;

    /** 主题管理员 userId 列表 */
    private List<ThemeMemberRequest> members;
}
