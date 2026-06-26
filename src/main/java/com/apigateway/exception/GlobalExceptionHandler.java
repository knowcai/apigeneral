package com.apigateway.exception;

import com.apigateway.dto.ApiResponse;
import com.apigateway.web.HttpErrorWriter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({AccessDeniedException.class, AuthenticationCredentialsNotFoundException.class})
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleAccessDenied(Exception ex) {
        return ApiResponse.fail(403, "无权访问");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("；"));
        return ApiResponse.fail(400, message.isEmpty() ? "参数校验失败" : message);
    }

    private String formatFieldError(FieldError error) {
        String field = switch (error.getField()) {
            case "password" -> "密码";
            case "username" -> "用户名";
            case "name" -> "名称";
            case "code" -> "编码";
            case "assigneeUserIds" -> "审批人";
            default -> error.getField();
        };
        if ("password".equals(error.getField())) {
            return field + "至少 6 位";
        }
        if ("username".equals(error.getField())) {
            return field + "不能为空";
        }
        return field + ": " + error.getDefaultMessage();
    }

    @ExceptionHandler(UnexpectedRollbackException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleUnexpectedRollback(UnexpectedRollbackException ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof BusinessException be) {
                return ApiResponse.fail(be.getCode() > 0 ? be.getCode() : 400, be.getMessage());
            }
            cause = cause.getCause();
        }
        return ApiResponse.fail("操作失败，请重试");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ApiResponse<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        String message = "不支持的 HTTP 方法: " + ex.getMethod();
        ApiResponse<Void> body = ApiResponse.fail(405, message);
        body.setRequestId(currentRequestId());
        return body;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusiness(BusinessException ex) {
        HttpStatus status = mapStatus(ex.getCode());
        ApiResponse<Object> body = new ApiResponse<>(ex.getCode(), ex.getMessage(), ex.getData(), currentRequestId());
        body.setRequestId(currentRequestId());
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleOther(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            message = "服务器内部错误，请稍后重试";
        }
        ApiResponse<Void> body = ApiResponse.fail(message);
        body.setRequestId(currentRequestId());
        return body;
    }

    private String currentRequestId() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            return HttpErrorWriter.requestId(servletAttrs.getRequest());
        }
        return null;
    }

    private HttpStatus mapStatus(int code) {
        return switch (code) {
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 429 -> HttpStatus.TOO_MANY_REQUESTS;
            case 202 -> HttpStatus.ACCEPTED;
            case 503 -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
