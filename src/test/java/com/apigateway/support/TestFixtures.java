package com.apigateway.support;

import com.apigateway.dto.ThemeMemberRequest;
import com.apigateway.dto.ThemeMembersUpdateRequest;
import com.apigateway.dto.ThemeRequest;
import com.apigateway.dto.ThemeResponse;
import com.apigateway.entity.SysUser;
import com.apigateway.entity.ThemeMembershipRole;
import com.apigateway.entity.UserRole;
import com.apigateway.repository.SysUserRepository;
import com.apigateway.service.ThemeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class TestFixtures {

    private final SysUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ThemeService themeService;

    public SysUser createUser(String username, UserRole role) {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setDisplayName(username);
        user.setRole(role);
        user.setEnabled(true);
        user.setPasswordHash(passwordEncoder.encode("password123"));
        return userRepository.save(user);
    }

    public SysUser requireSuperAdmin() {
        return userRepository.findByUsername("testadmin").orElseThrow();
    }

    public ThemeResponse createThemeWithAdmins(String name, List<Long> adminIds) {
        TestAuth.login(requireSuperAdmin().getId(), "testadmin", UserRole.SUPER_ADMIN);
        ThemeRequest req = new ThemeRequest();
        req.setName(name);
        req.setDescription("test theme");
        req.setEnabled(true);
        List<ThemeMemberRequest> members = new ArrayList<>();
        for (Long adminId : adminIds) {
            ThemeMemberRequest m = new ThemeMemberRequest();
            m.setUserId(adminId);
            m.setRole(ThemeMembershipRole.THEME_ADMIN);
            members.add(m);
        }
        req.setMembers(members);
        return themeService.create(req);
    }

    public void assignMembers(Long themeId, List<Long> memberIds) {
        TestAuth.login(requireSuperAdmin().getId(), "testadmin", UserRole.SUPER_ADMIN);
        ThemeMembersUpdateRequest req = new ThemeMembersUpdateRequest();
        req.setUserIds(memberIds);
        themeService.updateThemeMembers(themeId, req);
    }
}
