package com.apigateway.service;

import com.apigateway.dto.LoginRequest;
import com.apigateway.dto.UserInfo;
import com.apigateway.dto.UserRequest;
import com.apigateway.entity.SysUser;
import com.apigateway.entity.UserRole;
import com.apigateway.exception.BusinessException;
import com.apigateway.repository.SysUserRepository;
import com.apigateway.security.AuthUser;
import com.apigateway.security.AuthzService;
import com.apigateway.security.CurrentUser;
import com.apigateway.config.GatewaySecurityProperties;
import com.apigateway.security.LoginRateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthzService authzService;
    private final CurrentUser currentUser;
    private final AuditLogService auditLogService;
    private final LoginRateLimiter loginRateLimiter;

    public Map<String, Object> login(LoginRequest req, String clientIp) {
        loginRateLimiter.check(clientIp, req.getUsername());
        SysUser user = repository.findByUsername(req.getUsername())
                .orElseThrow(() -> new BusinessException(401, "用户名或密码错误"));
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new BusinessException(403, "账号已禁用");
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        String token = jwtService.generateToken(user.getId(), user.getUsername(), user.getRole().name());
        auditLogService.logAs(user.getId(), user.getUsername(), "LOGIN", "USER",
                String.valueOf(user.getId()), user.getUsername(), null);
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", toInfo(user));
        return result;
    }

    public UserInfo me() {
        return toInfo(currentUser.requireEntity());
    }

    public List<SysUser> list() {
        authzService.requireSuperAdmin();
        return repository.findAll();
    }

    public List<UserInfo> listRegularUsers() {
        authzService.requireAuthenticated();
        if (!authzService.isSuperAdmin()) {
            throw new BusinessException(403, "无权访问");
        }
        return repository.findAll().stream()
                .filter(u -> u.getRole() != UserRole.SUPER_ADMIN)
                .map(this::toInfo)
                .toList();
    }

    @Transactional
    public SysUser create(UserRequest req) {
        authzService.requireSuperAdmin();
        if (repository.findByUsername(req.getUsername()).isPresent()) {
            throw new BusinessException("用户名已存在");
        }
        UserRole role = req.getRole() != null ? req.getRole() : UserRole.API_EDITOR;
        if (role == UserRole.SUPER_ADMIN) {
            assertRoleRules(null, role);
        } else if (role != UserRole.API_EDITOR && role != UserRole.API_VIEWER) {
            throw new BusinessException("仅可创建普通用户");
        }
        req.setRole(role);
        SysUser user = new SysUser();
        apply(user, req, true);
        SysUser saved = repository.save(user);
        auditLogService.log("CREATE", "USER", String.valueOf(saved.getId()), saved.getUsername(), toInfo(saved));
        return saved;
    }

    @Transactional
    public SysUser update(Long id, UserRequest req) {
        authzService.requireSuperAdmin();
        SysUser user = get(id);
        if (!user.getUsername().equals(req.getUsername())
                && repository.findByUsername(req.getUsername()).isPresent()) {
            throw new BusinessException("用户名已存在");
        }
        UserRole role = user.getRole();
        if (role == UserRole.SUPER_ADMIN) {
            req.setRole(UserRole.SUPER_ADMIN);
        } else if (req.getRole() == UserRole.API_VIEWER) {
            req.setRole(UserRole.API_VIEWER);
        } else {
            req.setRole(UserRole.API_EDITOR);
        }
        apply(user, req, false);
        SysUser saved = repository.save(user);
        auditLogService.log("UPDATE", "USER", String.valueOf(saved.getId()), saved.getUsername(), toInfo(saved));
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        authzService.requireSuperAdmin();
        SysUser user = get(id);
        if (user.getRole() == UserRole.SUPER_ADMIN) {
            throw new BusinessException("不能删除超级管理员");
        }
        repository.delete(user);
        auditLogService.log("DELETE", "USER", String.valueOf(id), user.getUsername(), null);
    }

    public SysUser get(Long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException("用户不存在"));
    }

    public AuthUser loadAuthUser(String username) {
        SysUser user = repository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(401, "用户不存在"));
        return new AuthUser(user.getId(), user.getUsername(), user.getDisplayName(),
                user.getRole(), user.getPasswordHash(), Boolean.TRUE.equals(user.getEnabled()));
    }

    private void assertRoleRules(Long editingId, UserRole role) {
        if (role == UserRole.SUPER_ADMIN) {
            long count = repository.countByRole(UserRole.SUPER_ADMIN);
            if (editingId != null) {
                SysUser existing = get(editingId);
                if (existing.getRole() == UserRole.SUPER_ADMIN) {
                    count--;
                }
            }
            if (count > 0) {
                throw new BusinessException("系统仅允许一个超级管理员");
            }
        }
    }

    private void apply(SysUser user, UserRequest req, boolean creating) {
        user.setUsername(req.getUsername().trim());
        user.setDisplayName(req.getDisplayName());
        user.setRole(req.getRole());
        user.setEnabled(req.getEnabled() == null || req.getEnabled());
        if (creating) {
            if (req.getPassword() == null || req.getPassword().length() < 6) {
                throw new BusinessException("密码至少 6 位");
            }
            user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        } else if (req.getPassword() != null && !req.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        }
    }

    public UserInfo toInfo(SysUser user) {
        return UserInfo.builder()
                .id(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .role(user.getRole())
                .enabled(user.getEnabled())
                .build();
    }
}
