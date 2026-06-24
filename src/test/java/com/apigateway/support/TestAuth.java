package com.apigateway.support;

import com.apigateway.entity.UserRole;
import com.apigateway.security.AuthUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

public final class TestAuth {

    private TestAuth() {
    }

    public static void login(Long id, String username, UserRole role) {
        AuthUser user = new AuthUser(id, username, username, role, "pwd", true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }
}
