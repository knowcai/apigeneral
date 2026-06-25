package com.apigateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "统一 API 响应")
public class ApiResponse<T> {

    @Schema(description = "业务码，0 表示成功", example = "0")
    private int code;

    @Schema(description = "提示信息", example = "success")
    private String message;

    @Schema(description = "响应数据")
    private T data;

    @Schema(description = "请求追踪 ID", example = "a1b2c3d4e5f6")
    private String requestId;

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "success", data, null);
    }

    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(-1, message, null, null);
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null, null);
    }
}
