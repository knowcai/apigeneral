package com.apigateway.exception;

import com.apigateway.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<?> handleBusiness(BusinessException ex) {
        HttpStatus status = mapStatus(ex.getCode());
        if (ex.getData() instanceof java.util.Map<?, ?> map && map.containsKey("code")) {
            return ResponseEntity.status(status).body(map);
        }
        if (ex.getData() != null) {
            return ResponseEntity.status(status).body(new ApiResponse<>(ex.getCode(), ex.getMessage(), ex.getData()));
        }
        return ResponseEntity.status(status).body(new ApiResponse<>(ex.getCode(), ex.getMessage(), null));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleOther(Exception ex) {
        return ApiResponse.fail(ex.getMessage());
    }

    private HttpStatus mapStatus(int code) {
        return switch (code) {
            case 403 -> HttpStatus.FORBIDDEN;
            case 429 -> HttpStatus.TOO_MANY_REQUESTS;
            case 503 -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
