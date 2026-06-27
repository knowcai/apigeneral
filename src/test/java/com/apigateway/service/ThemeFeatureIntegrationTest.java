package com.apigateway.service;

import com.apigateway.dto.ThemeApiKeyRequest;
import com.apigateway.dto.ThemeResponse;
import com.apigateway.entity.*;
import com.apigateway.exception.BusinessException;
import com.apigateway.repository.ApiAccessLogRepository;
import com.apigateway.repository.ThemeRepository;
import com.apigateway.support.GatewayTestFixtures;
import com.apigateway.support.TestAuth;
import com.apigateway.support.TestFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class ThemeFeatureIntegrationTest {

    @Autowired
    private ThemeService themeService;
    @Autowired
    private ThemeApiKeyService themeApiKeyService;
    @Autowired
    private ApprovalService approvalService;
    @Autowired
    private MonitoringService monitoringService;
    @Autowired
    private TestFixtures fixtures;
    @Autowired
    private GatewayTestFixtures gatewayFixtures;
    @Autowired
    private ThemeRepository themeRepository;
    @Autowired
    private ApiAccessLogRepository accessLogRepository;

    private SysUser admin1;
    private SysUser admin2;
    private ThemeResponse theme;

    @BeforeEach
    void setUp() {
        admin1 = fixtures.createUser("feat_admin1", UserRole.API_EDITOR);
        admin2 = fixtures.createUser("feat_admin2", UserRole.API_EDITOR);
        theme = fixtures.createThemeWithAdmins("功能测试主题", List.of(admin1.getId(), admin2.getId()));
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    @Test
    void superAdminCanDeleteThemeWhenNoApis() {
        gatewayFixtures.createThemeApiKey(theme.getId(), "delete-theme-key");
        assertTrue(themeRepository.findById(theme.getId()).isPresent());

        TestAuth.login(fixtures.requireSuperAdmin().getId(), "testadmin", UserRole.SUPER_ADMIN);
        themeService.delete(theme.getId());

        assertTrue(themeRepository.findById(theme.getId()).isEmpty());
    }

    @Test
    void cannotDeleteThemeWhenApisExist() {
        var ds = gatewayFixtures.createDatasource(theme.getId(), "ds-for-delete");
        gatewayFixtures.publishApi(theme.getId(), "api-block-delete", ds.getId());

        TestAuth.login(fixtures.requireSuperAdmin().getId(), "testadmin", UserRole.SUPER_ADMIN);
        BusinessException ex = assertThrows(BusinessException.class, () -> themeService.delete(theme.getId()));
        assertTrue(ex.getMessage().contains("API"));
        assertTrue(themeRepository.findById(theme.getId()).isPresent());
    }

    @Test
    void themeAdminCannotDeleteTheme() {
        TestAuth.login(admin1.getId(), admin1.getUsername(), UserRole.API_EDITOR);
        BusinessException ex = assertThrows(BusinessException.class, () -> themeService.delete(theme.getId()));
        assertEquals(403, ex.getCode());
    }

    @Test
    void approveDeleteKeyRemovesConsumer() {
        TestAuth.login(fixtures.requireSuperAdmin().getId(), "testadmin", UserRole.SUPER_ADMIN);
        var created = themeApiKeyService.createDirect(theme.getId(), keyReq("to-delete"));
        Long keyId = created.getConsumer().getId();

        TestAuth.login(admin1.getId(), admin1.getUsername(), UserRole.API_EDITOR);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> themeApiKeyService.deleteKey(theme.getId(), keyId));
        assertEquals(202, ex.getCode());

        TestAuth.login(admin2.getId(), admin2.getUsername(), UserRole.API_EDITOR);
        Long taskId = pendingTaskForAssignee(admin2.getId(), admin2.getUsername());
        approvalService.approveTask(taskId, true, "同意删除");

        assertFalse(themeApiKeyService.authenticate(created.getApiKey()).isPresent());
    }

    @Test
    void monitoringDashboardIncludesApiKeyUsage() {
        var key = gatewayFixtures.createThemeApiKey(theme.getId(), "monitor-key");
        var ds = gatewayFixtures.createDatasource(theme.getId(), "ds-monitor");
        var published = gatewayFixtures.publishApi(theme.getId(), "api-monitor", ds.getId());

        ApiAccessLog log = new ApiAccessLog();
        log.setRequestId(UUID.randomUUID().toString());
        log.setApiCode(published.definition().getApiCode());
        log.setApiVersion(1);
        log.setConsumerId(key.getConsumer().getId());
        log.setConsumerName(key.getConsumer().getName());
        log.setStatus("SUCCESS");
        log.setDurationMs(42L);
        log.setCreatedAt(LocalDateTime.now());
        accessLogRepository.save(log);

        TestAuth.login(fixtures.requireSuperAdmin().getId(), "testadmin", UserRole.SUPER_ADMIN);
        Map<String, Object> dashboard = monitoringService.dashboard(24);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> usage = (List<Map<String, Object>>) dashboard.get("apiKeyUsage");
        assertNotNull(usage);
        assertTrue(usage.stream().anyMatch(row ->
                "api-monitor".equals(row.get("apiCode"))
                        && "monitor-key".equals(row.get("consumerName"))
                        && key.getConsumer().getId().equals(row.get("consumerId"))));
    }

    private ThemeApiKeyRequest keyReq(String name) {
        ThemeApiKeyRequest req = new ThemeApiKeyRequest();
        req.setName(name);
        return req;
    }

    private Long pendingTaskForAssignee(Long assigneeId, String username) {
        TestAuth.login(assigneeId, username, UserRole.API_EDITOR);
        List<Map<String, Object>> tasks = approvalService.listMyPendingTasks();
        assertFalse(tasks.isEmpty());
        return ((Number) tasks.get(0).get("taskId")).longValue();
    }
}
