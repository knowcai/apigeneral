package com.apigateway.service;

import com.apigateway.dto.ThemeMembersUpdateRequest;
import com.apigateway.dto.ThemeRequest;
import com.apigateway.dto.ThemeResponse;
import com.apigateway.entity.SysUser;
import com.apigateway.entity.ThemeMembershipRole;
import com.apigateway.entity.UserRole;
import com.apigateway.exception.BusinessException;
import com.apigateway.repository.ThemeMembershipRepository;
import com.apigateway.support.TestAuth;
import com.apigateway.support.TestFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class ThemeServiceIntegrationTest {

    @Autowired
    private ThemeService themeService;
    @Autowired
    private TestFixtures fixtures;
    @Autowired
    private ThemeMembershipRepository membershipRepository;

    private SysUser admin1;
    private SysUser admin2;
    private SysUser member1;

    @BeforeEach
    void setUp() {
        admin1 = fixtures.createUser("theme_admin1", UserRole.API_EDITOR);
        admin2 = fixtures.createUser("theme_admin2", UserRole.API_EDITOR);
        member1 = fixtures.createUser("theme_member1", UserRole.API_EDITOR);
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    @Test
    void themeNameMustBeUnique() {
        fixtures.createThemeWithAdmins("唯一主题A", List.of(admin1.getId()));

        TestAuth.login(fixtures.requireSuperAdmin().getId(), "testadmin", UserRole.SUPER_ADMIN);
        ThemeRequest dup = new ThemeRequest();
        dup.setName("唯一主题A");
        dup.setMembers(List.of());

        BusinessException ex = assertThrows(BusinessException.class, () -> themeService.create(dup));
        assertTrue(ex.getMessage().contains("名称已存在") || ex.getMessage().contains("主题管理员"));
    }

    @Test
    void themeCodeIsAutoIncrementId() {
        ThemeResponse theme = fixtures.createThemeWithAdmins("编码自增主题", List.of(admin1.getId()));
        assertEquals(String.valueOf(theme.getId()), theme.getCode());
    }

    @Test
    void superAdminCanManageThemeMembers() {
        ThemeResponse theme = fixtures.createThemeWithAdmins("成员管理主题", List.of(admin1.getId()));
        fixtures.assignMembers(theme.getId(), List.of(member1.getId()));

        assertTrue(membershipRepository.findByThemeIdAndUserId(theme.getId(), member1.getId())
                .filter(m -> m.getRole() == ThemeMembershipRole.MEMBER)
                .isPresent());
    }

    @Test
    void themeAdminCanManageMembers() {
        ThemeResponse theme = fixtures.createThemeWithAdmins("管理员维护成员", List.of(admin1.getId()));

        TestAuth.login(admin1.getId(), admin1.getUsername(), UserRole.API_EDITOR);
        ThemeMembersUpdateRequest req = new ThemeMembersUpdateRequest();
        req.setUserIds(List.of(member1.getId()));
        themeService.updateThemeMembers(theme.getId(), req);

        assertTrue(membershipRepository.findByThemeIdAndUserId(theme.getId(), member1.getId()).isPresent());
    }
}
