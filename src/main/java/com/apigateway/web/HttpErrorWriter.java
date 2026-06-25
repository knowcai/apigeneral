package com.apigateway.web;

import com.apigateway.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class HttpErrorWriter {

    public static final String REQUEST_ID_ATTR = "requestId";

    private final ObjectMapper objectMapper;

    public void write(HttpServletRequest request, HttpServletResponse response, int code, String message)
            throws IOException {
        response.setStatus(code);
        response.setContentType("application/json;charset=UTF-8");
        String requestId = requestId(request);
        if (requestId != null) {
            response.setHeader("X-Request-Id", requestId);
        }
        ApiResponse<Void> body = ApiResponse.fail(code, message);
        body.setRequestId(requestId);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    public static String requestId(HttpServletRequest request) {
        Object id = request.getAttribute(REQUEST_ID_ATTR);
        return id != null ? id.toString() : null;
    }
}
