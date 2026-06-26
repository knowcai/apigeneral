package com.apigateway.dto;

import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
public class ApprovalDiffResult {

    private final List<Map<String, Object>> diff;
    private final String error;

    private ApprovalDiffResult(List<Map<String, Object>> diff, String error) {
        this.diff = diff != null ? diff : List.of();
        this.error = error;
    }

    public static ApprovalDiffResult ok(List<Map<String, Object>> diff) {
        return new ApprovalDiffResult(diff, null);
    }

    public static ApprovalDiffResult empty() {
        return new ApprovalDiffResult(List.of(), null);
    }

    public static ApprovalDiffResult parseError(String message) {
        return new ApprovalDiffResult(List.of(), message);
    }
}
