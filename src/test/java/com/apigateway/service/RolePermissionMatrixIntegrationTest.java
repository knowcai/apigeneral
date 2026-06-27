package com.apigateway.service;

import com.apigateway.dto.*;
import com.apigateway.entity.*;
import com.apigateway.exception.BusinessException;
import com.apigateway.repository.ApiAccessLogRepository;
import com.apigateway.repository.ApiDefinitionRepository;
import com.apigateway.repository.DatasourceRepository;
import com.apigateway.support.GatewayTestFixtures;
import com.apigateway.support.TestAuth;
import com.apigateway.support.TestFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 按角色 × 功能覆盖权限矩阵（服务层，参见 docs/PERMISSIONS.md）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class RolePermissionMatrixIntegrationTest {

    @Autowired
    private ThemeService themeService;
    @Autowired
    private ThemeApiKeyService themeApiKeyService;
    @Autowired
    private DatasourceService datasourceService;
    @Autowired
    private ApiManagementService apiManagementService;
    @Autowired
    private GatewayPolicyService gatewayPolicyService;
    @Autowired
    private UserService userService;
    @Autowired
    private ConsumerService consumerService;
    @Autowired
    private AuditLogService auditLogService;
    @Autowired
    private AccessLogService accessLogService;
    @Autowired
    private ObservabilityScopeService observabilityScopeService;
    @Autowired
    private MonitoringService monitoringService;
    @Autowired
    private TestFixtures fixtures;
    @Autowired
    private GatewayTestFixtures gatewayFixtures;
    @Autowired
    private ApiAccessLogRepository accessLogRepository;
    @Autowired
    private ApiDefinitionRepository apiDefinitionRepository;
    @Autowired
    private DatasourceRepository datasourceRepository;

    private SysUser themeAdmin;
    private SysUser themeAdmin2;
    private SysUser themeMember;
    private SysUser viewer;
    private SysUser noThemeUser;
    private ThemeResponse theme;
    private ThemeResponse foreignTheme;

    @BeforeEach
    void setUp() {
        themeAdmin = fixtures.createUser("perm_tadmin", UserRole.API_EDITOR);
        themeAdmin2 = fixtures.createUser("perm_tadmin2", UserRole.API_EDITOR);
        themeMember = fixtures.createUser("perm_member", UserRole.API_EDITOR);
        viewer = fixtures.createUser("perm_viewer", UserRole.API_VIEWER);
        noThemeUser = fixtures.createUser("perm_none", UserRole.API_EDITOR);
        theme = fixtures.createThemeWithAdmins("权限矩阵-A", List.of(themeAdmin.getId(), themeAdmin2.getId()));
        fixtures.assignMembers(theme.getId(), List.of(themeMember.getId(), viewer.getId()));
        SysUser foreignAdmin = fixtures.createUser("perm_foreign", UserRole.API_EDITOR);
        foreignTheme = fixtures.createThemeWithAdmins("权限矩阵-B", List.of(foreignAdmin.getId()));
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    private void login(SysUser user) {
        TestAuth.login(user.getId(), user.getUsername(), user.getRole());
    }

    private void loginSuperAdmin() {
        SysUser sa = fixtures.requireSuperAdmin();
        TestAuth.login(sa.getId(), sa.getUsername(), UserRole.SUPER_ADMIN);
    }

    private DatasourceRequest dsReq(String name, Long themeId) {
        DatasourceRequest req = new DatasourceRequest();
        req.setName(name);
        req.setType(DatasourceType.POSTGRES);
        req.setHost("localhost");
        req.setPort(5432);
        req.setDatabaseName("test");
        req.setUsername("u");
        req.setPassword("pwd");
        req.setThemeId(themeId);
        return req;
    }

    private ApiDefinitionRequest apiReq(String code, Long themeId) {
        ApiDefinitionRequest req = new ApiDefinitionRequest();
        req.setApiCode(code);
        req.setName(code);
        req.setThemeId(themeId);
        return req;
    }

    private ThemeApiKeyRequest keyReq(String name) {
        ThemeApiKeyRequest req = new ThemeApiKeyRequest();
        req.setName(name);
        return req;
    }

    private GatewayPolicyRequest policyReq() {
        GatewayPolicyRequest req = new GatewayPolicyRequest();
        req.setGlobalQpsEnabled(true);
        req.setGlobalQps(500);
        req.setIpQpsEnabled(true);
        req.setIpQps(50);
        req.setApiQpsEnabled(true);
        req.setApiQps(25);
        req.setCircuitEnabled(true);
        req.setCircuitFailureRate(50);
        req.setCircuitMinCalls(10);
        req.setCircuitWaitSec(30);
        req.setRetryEnabled(false);
        req.setRetryMaxAttempts(1);
        req.setRetryIntervalMs(100);
        return req;
    }

    @Nested
    class ThemeManagement {

        @Test
        void superAdminCanCreateTheme() {
            loginSuperAdmin();
            ThemeRequest req = new ThemeRequest();
            req.setName("超管新建主题");
            req.setEnabled(true);
            ThemeMemberRequest m = new ThemeMemberRequest();
            m.setUserId(themeAdmin.getId());
            m.setRole(ThemeMembershipRole.THEME_ADMIN);
            req.setMembers(List.of(m));
            assertNotNull(themeService.create(req).getId());
        }

        @Test
        void themeAdminCannotCreateTheme() {
            login(themeAdmin);
            ThemeRequest req = new ThemeRequest();
            req.setName("越权主题");
            req.setEnabled(true);
            BusinessException ex = assertThrows(BusinessException.class, () -> themeService.create(req));
            assertEquals(403, ex.getCode());
        }

        @Test
        void themeMemberCannotUpdateMembers() {
            login(themeMember);
            ThemeMembersUpdateRequest req = new ThemeMembersUpdateRequest();
            req.setUserIds(List.of());
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> themeService.updateThemeMembers(theme.getId(), req));
            assertEquals(403, ex.getCode());
        }

        @Test
        void themeAdminCanUpdateMembers() {
            login(themeAdmin);
            ThemeMembersUpdateRequest req = new ThemeMembersUpdateRequest();
            req.setUserIds(List.of(themeMember.getId()));
            assertNotNull(themeService.updateThemeMembers(theme.getId(), req));
        }

        @Test
        void userWithoutThemeSeesEmptyThemeList() {
            login(noThemeUser);
            assertTrue(themeService.listAccessible().isEmpty());
        }
    }

    @Nested
    class ThemeApiKey {

        @Test
        void superAdminCannotCreateKey() {
            loginSuperAdmin();
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> themeApiKeyService.create(theme.getId(), keyReq("sa-key")));
            assertEquals(403, ex.getCode());
        }

        @Test
        void superAdminListKeysReadOnly() {
            gatewayFixtures.createThemeApiKey(theme.getId(), "readonly-key");
            loginSuperAdmin();
            ThemeApiKeyListResponse list = themeApiKeyService.listKeys(theme.getId());
            assertFalse(list.isCanManage());
            assertEquals(1, list.getUsedSlots());
        }

        @Test
        void themeAdminCreateGoesToApproval() {
            login(themeAdmin);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> themeApiKeyService.create(theme.getId(), keyReq("admin-key")));
            assertEquals(202, ex.getCode());
        }

        @Test
        void themeMemberCannotCreateKey() {
            login(themeMember);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> themeApiKeyService.create(theme.getId(), keyReq("member-key")));
            assertEquals(403, ex.getCode());
        }

        @Test
        void superAdminCannotDeleteKey() {
            loginSuperAdmin();
            var created = themeApiKeyService.createDirect(theme.getId(), keyReq("del-sa"));
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> themeApiKeyService.deleteKey(theme.getId(), created.getConsumer().getId()));
            assertEquals(403, ex.getCode());
        }

        @Test
        void themeAdminDeleteGoesToApproval() {
            loginSuperAdmin();
            var created = themeApiKeyService.createDirect(theme.getId(), keyReq("del-admin"));
            login(themeAdmin);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> themeApiKeyService.deleteKey(theme.getId(), created.getConsumer().getId()));
            assertEquals(202, ex.getCode());
        }
    }

    @Nested
    class DatasourceOps {

        @Test
        void superAdminCreatesDirectly() {
            loginSuperAdmin();
            DatasourceResponse created = datasourceService.create(dsReq("sa-ds", theme.getId()));
            assertNotNull(created.getId());
            assertTrue(datasourceRepository.findById(created.getId()).isPresent());
        }

        @Test
        void themeAdminCreateGoesToApproval() {
            login(themeAdmin);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> datasourceService.create(dsReq("admin-ds", theme.getId())));
            assertEquals(202, ex.getCode());
        }

        @Test
        void themeMemberCreateGoesToApproval() {
            login(themeMember);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> datasourceService.create(dsReq("member-ds", theme.getId())));
            assertEquals(202, ex.getCode());
        }

        @Test
        void themeMemberCannotDeleteDatasource() {
            var ds = gatewayFixtures.createDatasource(theme.getId(), "member-del-ds");
            login(themeMember);
            BusinessException ex = assertThrows(BusinessException.class, () -> datasourceService.delete(ds.getId()));
            assertEquals(403, ex.getCode());
        }

        @Test
        void themeAdminDeleteGoesToApproval() {
            var ds = gatewayFixtures.createDatasource(theme.getId(), "admin-del-ds");
            login(themeAdmin);
            BusinessException ex = assertThrows(BusinessException.class, () -> datasourceService.delete(ds.getId()));
            assertEquals(202, ex.getCode());
        }

        @Test
        void userWithoutThemeCannotListForeignDatasource() {
            var ds = gatewayFixtures.createDatasource(theme.getId(), "scoped-ds");
            login(noThemeUser);
            BusinessException ex = assertThrows(BusinessException.class, () -> datasourceService.get(ds.getId()));
            assertEquals(403, ex.getCode());
        }
    }

    @Nested
    class ApiDefinitionOps {

        @Test
        void superAdminCreatesDirectly() {
            loginSuperAdmin();
            ApiDefinition def = apiManagementService.createDefinition(apiReq("sa-api", theme.getId()));
            assertNotNull(def.getId());
        }

        @Test
        void themeMemberCreateGoesToApproval() {
            login(themeMember);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> apiManagementService.createDefinition(apiReq("member-api", theme.getId())));
            assertEquals(202, ex.getCode());
        }

        @Test
        void themeMemberCannotDeleteApi() {
            var ds = gatewayFixtures.createDatasource(theme.getId(), "api-del-ds");
            var published = gatewayFixtures.publishApi(theme.getId(), "member-no-del", ds.getId());
            login(themeMember);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> apiManagementService.deleteDefinition(published.definition().getId()));
            assertEquals(403, ex.getCode());
        }

        @Test
        void themeAdminDeleteGoesToApproval() {
            var ds = gatewayFixtures.createDatasource(theme.getId(), "api-admin-del-ds");
            var published = gatewayFixtures.publishApi(theme.getId(), "admin-del-api", ds.getId());
            login(themeAdmin);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> apiManagementService.deleteDefinition(published.definition().getId()));
            assertEquals(202, ex.getCode());
        }

        @Test
        void viewerCannotCreateApi() {
            login(viewer);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> apiManagementService.createDefinition(apiReq("viewer-api", theme.getId())));
            assertEquals(403, ex.getCode());
        }

        @Test
        void viewerCanListApisInTheme() {
            var ds = gatewayFixtures.createDatasource(theme.getId(), "viewer-read-ds");
            gatewayFixtures.publishApi(theme.getId(), "viewer-read-api", ds.getId());
            login(viewer);
            assertFalse(apiManagementService.listDefinitions().isEmpty());
        }
    }

    @Nested
    class GatewayPolicyAccess {

        @Test
        void superAdminCanUpdatePolicy() {
            loginSuperAdmin();
            com.apigateway.entity.GatewayPolicy updated = gatewayPolicyService.update(policyReq());
            assertEquals(500, updated.getGlobalQps());
        }

        @Test
        void themeAdminCannotUpdatePolicy() {
            login(themeAdmin);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> gatewayPolicyService.update(policyReq()));
            assertEquals(403, ex.getCode());
        }

        @Test
        void viewerCannotUpdatePolicy() {
            login(viewer);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> gatewayPolicyService.update(policyReq()));
            assertEquals(403, ex.getCode());
        }

        @Test
        void anyoneCanReadPolicyRuntime() {
            login(themeMember);
            assertNotNull(gatewayPolicyService.get());
        }
    }

    @Nested
    class PlatformAdmin {

        @Test
        void superAdminCanListUsers() {
            loginSuperAdmin();
            assertFalse(userService.list().isEmpty());
        }

        @Test
        void editorCannotListUsers() {
            login(themeAdmin);
            BusinessException ex = assertThrows(BusinessException.class, () -> userService.list());
            assertEquals(403, ex.getCode());
        }

        @Test
        void superAdminCanListConsumers() {
            loginSuperAdmin();
            assertNotNull(consumerService.list());
        }

        @Test
        void editorCannotListConsumers() {
            login(themeAdmin);
            BusinessException ex = assertThrows(BusinessException.class, () -> consumerService.list());
            assertEquals(403, ex.getCode());
        }

        @Test
        void superAdminCanListAuditLogs() {
            loginSuperAdmin();
            assertNotNull(auditLogService.list(PageRequest.of(0, 10)));
        }

        @Test
        void themeAdminCannotListFullAuditLogs() {
            login(themeAdmin);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> auditLogService.list(PageRequest.of(0, 10)));
            assertEquals(403, ex.getCode());
        }

        @Test
        void themeAdminCanListThemeApiKeyAuditEvents() {
            login(themeAdmin);
            assertNotNull(auditLogService.listThemeApiKeyEvents(PageRequest.of(0, 10)));
        }
    }

    @Nested
    class Observability {

        @Test
        void themeAdminScopeLimitedToOwnTheme() {
            var dsA = gatewayFixtures.createDatasource(theme.getId(), "obs-ds-a");
            gatewayFixtures.publishApi(theme.getId(), "obs-api-a", dsA.getId());
            var dsB = gatewayFixtures.createDatasource(foreignTheme.getId(), "obs-ds-b");
            gatewayFixtures.publishApi(foreignTheme.getId(), "obs-api-b", dsB.getId());

            saveAccessLog("obs-api-a", "key-a");
            saveAccessLog("obs-api-b", "key-b");

            login(themeAdmin);
            ObservabilityScopeService.Scope scope = observabilityScopeService.currentScope();
            assertFalse(scope.global());
            assertTrue(scope.apiCodes().contains("obs-api-a"));
            assertFalse(scope.apiCodes().contains("obs-api-b"));

            AccessLogQuery query = new AccessLogQuery();
            query.setPage(0);
            query.setSize(50);
            var page = accessLogService.search(query);
            assertTrue(page.getContent().stream().anyMatch(v -> "obs-api-a".equals(v.getApiCode())));
            assertTrue(page.getContent().stream().noneMatch(v -> "obs-api-b".equals(v.getApiCode())));
        }

        @Test
        void superAdminScopeIsGlobal() {
            loginSuperAdmin();
            ObservabilityScopeService.Scope scope = observabilityScopeService.currentScope();
            assertTrue(scope.global());
            assertNotNull(monitoringService.dashboard(24));
        }

        @Test
        void userWithoutThemeHasEmptyScope() {
            login(noThemeUser);
            ObservabilityScopeService.Scope scope = observabilityScopeService.currentScope();
            assertTrue(scope.isEmpty());
        }
    }

    private void saveAccessLog(String apiCode, String consumerName) {
        ApiAccessLog log = new ApiAccessLog();
        log.setRequestId(UUID.randomUUID().toString());
        log.setApiCode(apiCode);
        log.setApiVersion(1);
        log.setConsumerName(consumerName);
        log.setStatus("SUCCESS");
        log.setDurationMs(10L);
        log.setCreatedAt(LocalDateTime.now());
        accessLogRepository.save(log);
    }
}
