package com.apigateway.dto;

import lombok.Data;

import java.util.List;

@Data
public class ThemeMembersUpdateRequest {

    /** 主题下的普通成员 userId 列表（不含主题管理员） */
    private List<Long> userIds;
}
