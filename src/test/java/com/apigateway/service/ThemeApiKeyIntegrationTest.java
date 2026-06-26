package com.apigateway.service;

import com.apigateway.dto.ApproveTaskResult;
import com.apigateway.dto.ThemeApiKeyRequest;
import com.apigateway.dto.ThemeResponse;
import com.apigateway.entity.*;
import com.apigateway.exception.BusinessException;
import com.apigateway.repository.ApprovalRequestRepository;
import com.apigateway.repository.ConsumerRepository;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class ThemeApiKeyIntegrationTest {

    @Autowired
    private ThemeApiKeyService themeApiKeyService;
    @Autowired
    private ConsumerService consumerService;
    @Autowired
    private ApprovalService approvalService;
    @Autowired
    private TestFixtures fixtures;
    @Autowired
    private GatewayTestFixtures gatewayFixtures;
    @Autowired
    private ConsumerRepository consumerRepository;
    @Autowired
    private ApprovalRequestRepository approvalRequestRepository;

    private SysUser admin1;
    private SysUser admin2;
    private ThemeResponse theme;

    @BeforeEach
    void setUp() {
        admin1 = fixtures.createUser("key_admin1", UserRole.API_EDITOR);
        admin2 = fixtures.createUser("key_admin2", UserRole.API_EDITOR);
        theme = fixtures.createThemeWithAdmins("主题 Key 测试", List.of(admin1.getId(), admin2.getId()));
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    @Test
    void superAdminCreateKeyDirect() {
        TestAuth.login(fixtures.requireSuperAdmin().getId(), "testadmin", UserRole.SUPER_ADMIN);
        ThemeApiKeyRequest req = new ThemeApiKeyRequest();
        req.setName("direct-key");

        var created = themeApiKeyService.createDirect(theme.getId(), req);
        assertNotNull(created.getApiKey());
        assertTrue(themeApiKeyService.authenticate(created.getApiKey()).isPresent());
        assertEquals(theme.getId(), created.getConsumer().getThemeId());
    }

    @Test
    void duplicateKeyRejected() {
        gatewayFixtures.createThemeApiKey(theme.getId(), "first-key");
        TestAuth.login(fixtures.requireSuperAdmin().getId(), "testadmin", UserRole.SUPER_ADMIN);

        ThemeApiKeyRequest req = new ThemeApiKeyRequest();
        req.setName("second-key");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> themeApiKeyService.createDirect(theme.getId(), req));
        assertTrue(ex.getMessage().contains("已有 API Key"));
    }

    @Test
    void themeAdminCreateGoesToApproval() {
        TestAuth.login(admin1.getId(), admin1.getUsername(), UserRole.API_EDITOR);
        ThemeApiKeyRequest req = new ThemeApiKeyRequest();
        req.setName("approval-key");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> themeApiKeyService.create(theme.getId(), req));
        assertEquals(202, ex.getCode());
        assertTrue(consumerRepository.findByThemeId(theme.getId()).isEmpty());
    }

    @Test
    void approveCreateKeyStoresPickup() {
        TestAuth.login(admin1.getId(), admin1.getUsername(), UserRole.API_EDITOR);
        ThemeApiKeyRequest req = new ThemeApiKeyRequest();
        req.setName("approved-key");
        assertThrows(BusinessException.class, () -> themeApiKeyService.create(theme.getId(), req));

        TestAuth.login(admin2.getId(), admin2.getUsername(), UserRole.API_EDITOR);
        Long taskId = pendingTaskForAssignee(admin2.getId(), admin2.getUsername());
        ApproveTaskResult outcome = approvalService.approveTask(taskId, true, "ok");

        assertTrue(outcome.isThemeKeyPickupPending());
        assertNull(outcome.getApiKey());
        assertTrue(consumerRepository.findByThemeId(theme.getId()).isPresent());
        assertEquals(ApprovalStatus.APPROVED,
                approvalRequestRepository.findAll().stream()
                        .filter(r -> r.getResourceType() == ApprovalResourceType.THEME_API_KEY)
                        .findFirst()
                        .orElseThrow()
                        .getStatus());
    }

    @Test
    void claimPickupReturnsKeyOnce() {
        TestAuth.login(admin1.getId(), admin1.getUsername(), UserRole.API_EDITOR);
        ThemeApiKeyRequest req = new ThemeApiKeyRequest();
        req.setName("claim-key");
        assertThrows(BusinessException.class, () -> themeApiKeyService.create(theme.getId(), req));

        TestAuth.login(admin2.getId(), admin2.getUsername(), UserRole.API_EDITOR);
        Long taskId = pendingTaskForAssignee(admin2.getId(), admin2.getUsername());
        approvalService.approveTask(taskId, true, "ok");

        TestAuth.login(admin1.getId(), admin1.getUsername(), UserRole.API_EDITOR);
        var claimed = themeApiKeyService.claimPickup(theme.getId());
        assertNotNull(claimed.getApiKey());
        assertTrue(themeApiKeyService.authenticate(claimed.getApiKey()).isPresent());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> themeApiKeyService.claimPickup(theme.getId()));
        assertTrue(ex.getMessage().contains("没有待领取"));
    }

    @Test
    void canAccessThemeApisAfterKeyCreated() {
        var ds = gatewayFixtures.createDatasource(theme.getId(), "key-access-ds");
        var published = gatewayFixtures.publishApi(theme.getId(), "key-access-api", ds.getId());
        var created = gatewayFixtures.createThemeApiKey(theme.getId(), "access-key");
        Long consumerId = created.getConsumer().getId();

        assertTrue(consumerService.canAccess(consumerId, published.definition().getId()));

        SysUser otherAdmin = fixtures.createUser("key_other_admin", UserRole.API_EDITOR);
        ThemeResponse otherTheme = fixtures.createThemeWithAdmins("Key 其他主题", List.of(otherAdmin.getId()));
        var otherDs = gatewayFixtures.createDatasource(otherTheme.getId(), "key-other-ds");
        var otherApi = gatewayFixtures.publishApi(otherTheme.getId(), "key-other-api", otherDs.getId());

        assertFalse(consumerService.canAccess(consumerId, otherApi.definition().getId()));
    }

    @Test
    void rotateKeyInvalidatesOldKey() {
        var created = gatewayFixtures.createThemeApiKey(theme.getId(), "rotate-key");
        String oldKey = created.getApiKey();
        assertTrue(themeApiKeyService.authenticate(oldKey).isPresent());

        TestAuth.login(fixtures.requireSuperAdmin().getId(), "testadmin", UserRole.SUPER_ADMIN);
        var rotated = themeApiKeyService.rotateDirect(
                consumerRepository.findByThemeId(theme.getId()).orElseThrow());
        String newKey = rotated.getApiKey();

        assertTrue(themeApiKeyService.authenticate(newKey).isPresent());
        assertTrue(themeApiKeyService.authenticate(oldKey).isEmpty());
    }

    @Test
    void themeAdminRotateGoesToApproval() {
        gatewayFixtures.createThemeApiKey(theme.getId(), "rotate-approval-key");
        TestAuth.login(admin1.getId(), admin1.getUsername(), UserRole.API_EDITOR);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> themeApiKeyService.rotate(theme.getId()));
        assertEquals(202, ex.getCode());
    }

    private Long pendingTaskForAssignee(Long assigneeId, String username) {
        TestAuth.login(assigneeId, username, UserRole.API_EDITOR);
        List<Map<String, Object>> tasks = approvalService.listMyPendingTasks();
        assertFalse(tasks.isEmpty(), "assignee " + assigneeId + " should have pending task");
        return ((Number) tasks.get(0).get("taskId")).longValue();
    }
}
