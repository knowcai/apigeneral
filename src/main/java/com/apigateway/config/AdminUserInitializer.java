package com.apigateway.config;

import com.apigateway.entity.SysUser;
import com.apigateway.entity.UserRole;
import com.apigateway.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminUserInitializer implements ApplicationRunner {

    private final SysUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${gateway.auth.default-admin-username:admin}")
    private String defaultUsername;

    @Value("${gateway.auth.default-admin-password:admin123}")
    private String defaultPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        SysUser admin = new SysUser();
        admin.setUsername(defaultUsername);
        admin.setDisplayName("超级管理员");
        admin.setRole(UserRole.SUPER_ADMIN);
        admin.setEnabled(true);
        admin.setPasswordHash(passwordEncoder.encode(defaultPassword));
        userRepository.save(admin);
    }
}
