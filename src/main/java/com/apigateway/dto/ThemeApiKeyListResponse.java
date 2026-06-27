package com.apigateway.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ThemeApiKeyListResponse {

    private List<ThemeApiKeyItemResponse> keys;
    private int usedSlots;
    private int maxSlots;
    private boolean canManage;
}
