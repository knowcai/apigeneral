package com.apigateway.security;

import com.apigateway.service.ConsumerService;
import com.apigateway.web.HttpErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final ConsumerService consumerService;
    private final HttpErrorWriter errorWriter;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/data/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String rawKey = extractKey(request);
        if (rawKey == null) {
            errorWriter.write(request, response, 401,
                    "缺少 API Key，请通过 Header 传入：Authorization: Bearer <key> 或 X-Api-Key: <key>");
            return;
        }
        var consumer = consumerService.authenticate(rawKey).orElse(null);
        if (consumer == null) {
            errorWriter.write(request, response, 401, "API Key 无效或已禁用");
            return;
        }
        request.setAttribute(ApiConsumerContext.REQUEST_ATTR, ApiConsumerContext.from(consumer));
        filterChain.doFilter(request, response);
    }

    private String extractKey(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            if (!token.isEmpty()) {
                return token;
            }
        }
        String apiKey = request.getHeader("X-Api-Key");
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey.trim();
        }
        return null;
    }
}

