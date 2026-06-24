package com.apigateway.service;

import com.apigateway.dto.*;
import com.apigateway.entity.*;
import com.apigateway.exception.BusinessException;
import com.apigateway.repository.*;
import com.apigateway.security.AuthzService;
import com.apigateway.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ThemeService {

    private final ThemeRepository themeRepository;
    private final ThemeMembershipRepository membershipRepository;
    private final SysUserRepository userRepository;
    private final AuthzService authzService;
    private final AuditLogService auditLogService;

    public List<ThemeResponse> listAccessible() {
        authzService.requireAuthenticated();
        if (authzService.isSuperAdmin()) {
            return themeRepository.findAll().stream().map(this::toResponse).toList();
        }
        Long uid = authzService.currentUserId();
        return membershipRepository.findByUserId(uid).stream()
                .map(m -> themeRepository.findById(m.getThemeId()).orElse(null))
                .filter(t -> t != null && Boolean.TRUE.equals(t.getEnabled()))
                .map(this::toResponse)
                .toList();
    }

    public ThemeResponse get(Long id) {
        requireThemeRead(id);
        return toResponse(themeRepository.findById(id).orElseThrow(() -> new BusinessException("主题不存在")));
    }

    @Transactional
    public ThemeResponse create(ThemeRequest req) {
        authzService.requireSuperAdmin();
        assertUniqueName(req.getName(), null);
        validateThemeAdmins(req);
        Theme theme = new Theme();
        theme.setCode("tmp-" + System.nanoTime());
        theme.setName(req.getName().trim());
        theme.setDescription(req.getDescription());
        theme.setEnabled(req.getEnabled() != null ? req.getEnabled() : true);
        Theme saved = themeRepository.save(theme);
        saved.setCode(String.valueOf(saved.getId()));
        saved = themeRepository.save(saved);
        saveThemeAdmins(saved.getId(), req.getMembers());
        auditLogService.log("CREATE", "THEME", String.valueOf(saved.getId()), saved.getCode(), saved);
        return toResponse(saved);
    }

    @Transactional
    public ThemeResponse update(Long id, ThemeRequest req) {
        authzService.requireSuperAdmin();
        Theme theme = themeRepository.findById(id).orElseThrow(() -> new BusinessException("主题不存在"));
        assertUniqueName(req.getName(), id);
        validateThemeAdmins(req);
        theme.setName(req.getName().trim());
        theme.setDescription(req.getDescription());
        if (req.getEnabled() != null) {
            theme.setEnabled(req.getEnabled());
        }
        Theme saved = themeRepository.save(theme);
        saveThemeAdmins(saved.getId(), req.getMembers());
        auditLogService.log("UPDATE", "THEME", String.valueOf(saved.getId()), saved.getCode(), saved);
        return toResponse(saved);
    }

    private void assertUniqueName(String name, Long excludeId) {
        if (name == null || name.isBlank()) {
            throw new BusinessException("主题名称不能为空");
        }
        String trimmed = name.trim();
        boolean exists = excludeId == null
                ? themeRepository.existsByName(trimmed)
                : themeRepository.existsByNameAndIdNot(trimmed, excludeId);
        if (exists) {
            throw new BusinessException("主题名称已存在");
        }
    }

    private void validateThemeAdmins(ThemeRequest req) {
        long adminCount = req.getMembers() == null ? 0
                : req.getMembers().stream().filter(m -> m.getRole() == ThemeMembershipRole.THEME_ADMIN).count();
        if (adminCount == 0) {
            throw new BusinessException("请至少指定一名主题管理员");
        }
    }

    public Theme requireEnabled(Long themeId) {
        Theme theme = themeRepository.findById(themeId).orElseThrow(() -> new BusinessException("主题不存在"));
        if (!Boolean.TRUE.equals(theme.getEnabled())) {
            throw new BusinessException("主题已禁用");
        }
        return theme;
    }

    public List<Long> accessibleThemeIds() {
        authzService.requireAuthenticated();
        if (authzService.isSuperAdmin()) {
            return themeRepository.findAll().stream().map(Theme::getId).toList();
        }
        return membershipRepository.findByUserId(authzService.currentUserId()).stream()
                .map(ThemeMembership::getThemeId)
                .toList();
    }

    public void requireThemeRead(Long themeId) {
        authzService.requireAuthenticated();
        if (authzService.isSuperAdmin()) {
            return;
        }
        Long uid = authzService.currentUserId();
        membershipRepository.findByThemeIdAndUserId(themeId, uid)
                .orElseThrow(() -> new BusinessException(403, "无权访问该主题"));
    }

    public void requireThemeWrite(Long themeId) {
        requireThemeRead(themeId);
        if (authzService.isSuperAdmin()) {
            return;
        }
        ThemeMembership m = membershipRepository.findByThemeIdAndUserId(themeId, authzService.currentUserId())
                .orElseThrow(() -> new BusinessException(403, "无权操作该主题"));
        if (m.getRole() != ThemeMembershipRole.THEME_ADMIN && m.getRole() != ThemeMembershipRole.MEMBER) {
            throw new BusinessException(403, "无权操作该主题");
        }
    }

    public void requireThemeAdmin(Long themeId) {
        requireThemeRead(themeId);
        if (authzService.isSuperAdmin()) {
            return;
        }
        ThemeMembership m = membershipRepository.findByThemeIdAndUserId(themeId, authzService.currentUserId())
                .orElseThrow(() -> new BusinessException(403, "需要主题管理员权限"));
        if (m.getRole() != ThemeMembershipRole.THEME_ADMIN) {
            throw new BusinessException(403, "需要主题管理员权限");
        }
    }

    public void requireThemeMemberManage(Long themeId) {
        requireThemeRead(themeId);
        if (authzService.isSuperAdmin()) {
            return;
        }
        requireThemeAdmin(themeId);
    }

    public boolean isThemeAdmin(Long themeId, Long userId) {
        if (authzService.isSuperAdmin()) {
            return true;
        }
        return membershipRepository.findByThemeIdAndUserId(themeId, userId)
                .map(m -> m.getRole() == ThemeMembershipRole.THEME_ADMIN)
                .orElse(false);
    }

    public long countThemeAdmins(Long themeId) {
        return membershipRepository.countByThemeIdAndRole(themeId, ThemeMembershipRole.THEME_ADMIN);
    }

    @Transactional
    public ThemeResponse updateThemeMembers(Long themeId, ThemeMembersUpdateRequest req) {
        requireThemeMemberManage(themeId);
        Theme theme = themeRepository.findById(themeId).orElseThrow(() -> new BusinessException("主题不存在"));
        List<Long> userIds = req.getUserIds() != null ? req.getUserIds() : List.of();
        for (Long uid : userIds) {
            SysUser user = userRepository.findById(uid).orElseThrow(() -> new BusinessException("用户不存在: " + uid));
            if (user.getRole() == UserRole.SUPER_ADMIN) {
                throw new BusinessException("不能将超级管理员设为主题成员");
            }
            membershipRepository.findByThemeIdAndUserId(themeId, uid).ifPresent(existing -> {
                if (existing.getRole() == ThemeMembershipRole.THEME_ADMIN) {
                    throw new BusinessException("用户 " + user.getUsername() + " 已是主题管理员，不能同时作为普通成员");
                }
            });
        }
        membershipRepository.findByThemeId(themeId).stream()
                .filter(m -> m.getRole() == ThemeMembershipRole.MEMBER)
                .forEach(membershipRepository::delete);
        for (Long uid : userIds) {
            if (membershipRepository.findByThemeIdAndUserId(themeId, uid).isPresent()) {
                continue;
            }
            ThemeMembership tm = new ThemeMembership();
            tm.setThemeId(themeId);
            tm.setUserId(uid);
            tm.setRole(ThemeMembershipRole.MEMBER);
            membershipRepository.save(tm);
        }
        auditLogService.log("UPDATE", "THEME_MEMBERS", String.valueOf(themeId), theme.getCode(), userIds);
        return toResponse(theme);
    }

    public List<UserInfo> listRegularUsersForTheme(Long themeId) {
        requireThemeRead(themeId);
        if (!authzService.isSuperAdmin() && !isThemeAdmin(themeId, authzService.currentUserId())) {
            throw new BusinessException(403, "需要主题管理员权限");
        }
        Set<Long> adminIds = membershipRepository.findByThemeId(themeId).stream()
                .filter(m -> m.getRole() == ThemeMembershipRole.THEME_ADMIN)
                .map(ThemeMembership::getUserId)
                .collect(Collectors.toSet());
        return listRegularUsersInternal().stream()
                .filter(u -> !adminIds.contains(u.getId()))
                .toList();
    }

    private List<UserInfo> listRegularUsersInternal() {
        return userRepository.findAll().stream()
                .filter(u -> u.getRole() != UserRole.SUPER_ADMIN)
                .map(u -> UserInfo.builder()
                        .id(u.getId())
                        .username(u.getUsername())
                        .displayName(u.getDisplayName())
                        .role(u.getRole())
                        .enabled(u.getEnabled())
                        .build())
                .toList();
    }

    private void saveThemeAdmins(Long themeId, List<ThemeMemberRequest> members) {
        membershipRepository.findByThemeId(themeId).stream()
                .filter(m -> m.getRole() == ThemeMembershipRole.THEME_ADMIN)
                .forEach(membershipRepository::delete);
        if (members == null) {
            return;
        }
        for (ThemeMemberRequest m : members) {
            if (m.getUserId() == null || m.getRole() != ThemeMembershipRole.THEME_ADMIN) {
                continue;
            }
            membershipRepository.findByThemeIdAndUserId(themeId, m.getUserId()).ifPresent(existing -> {
                if (existing.getRole() == ThemeMembershipRole.MEMBER) {
                    membershipRepository.delete(existing);
                }
            });
            ThemeMembership tm = new ThemeMembership();
            tm.setThemeId(themeId);
            tm.setUserId(m.getUserId());
            tm.setRole(ThemeMembershipRole.THEME_ADMIN);
            membershipRepository.save(tm);
        }
    }

    private ThemeResponse toResponse(Theme theme) {
        List<ThemeMembership> members = membershipRepository.findByThemeId(theme.getId());
        List<ThemeResponse.Member> memberDtos = members.stream().map(m -> {
            SysUser u = userRepository.findById(m.getUserId()).orElse(null);
            return ThemeResponse.Member.builder()
                    .userId(m.getUserId())
                    .username(u != null ? u.getUsername() : null)
                    .displayName(u != null ? u.getDisplayName() : null)
                    .role(m.getRole())
                    .build();
        }).toList();

        return ThemeResponse.builder()
                .id(theme.getId())
                .code(String.valueOf(theme.getId()))
                .name(theme.getName())
                .description(theme.getDescription())
                .enabled(theme.getEnabled())
                .members(memberDtos)
                .createdAt(theme.getCreatedAt())
                .myRole(resolveMyRole(theme.getId()))
                .build();
    }

    private ThemeMembershipRole resolveMyRole(Long themeId) {
        if (authzService.isSuperAdmin()) {
            return null;
        }
        return membershipRepository.findByThemeIdAndUserId(themeId, authzService.currentUserId())
                .map(ThemeMembership::getRole)
                .orElse(null);
    }
}
