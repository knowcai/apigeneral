package com.apigateway.service;

import com.apigateway.dto.ApproveTaskResult;
import com.apigateway.dto.ThemeApiKeyRequest;
import com.apigateway.dto.ThemeResponse;
import com.apigateway.entity.*;
import com.apigateway.exception.BusinessException;
import com.apigateway.repository.ApprovalRequestRepository;
import com.apigateway.repository.ConsumerRepository;
import com.apigateway.repository.ThemeApiKeyPickupRepository;
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

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class ThemeApiKeyIntegrationTest {

    @Autowired
    private ThemeApiKeyService themeApiKeyService;
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
    @Autowired
    private ThemeApiKeyPickupRepository pickupRepository;

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
    void superAdminCannotCreateKey() {
        TestAuth.login(fixtures.requireSuperAdmin().getId(), "testadmin", UserRole.SUPER_ADMIN);
        ThemeApiKeyRequest req = new ThemeApiKeyRequest();
        req.setName("direct-key");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> themeApiKeyService.create(theme.getId(), req));
        assertEquals(403, ex.getCode());
        assertEquals(0, themeApiKeyService.countUsedSlots(theme.getId()));
    }

    @Test
    void themeAdminCreateGoesToApproval() {
        TestAuth.login(admin1.getId(), admin1.getUsername(), UserRole.API_EDITOR);
        ThemeApiKeyRequest req = new ThemeApiKeyRequest();
        req.setName("approval-key");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> themeApiKeyService.create(theme.getId(), req));
        assertEquals(202, ex.getCode());
        assertTrue(consumerRepository.findAllByThemeIdOrderByCreatedAtAsc(theme.getId()).isEmpty());
        assertEquals(1, themeApiKeyService.countUsedSlots(theme.getId()));
    }

    @Test
    void approveCreateKeyStoresPickupForSubmitter() {
        TestAuth.login(admin1.getId(), admin1.getUsername(), UserRole.API_EDITOR);
        ThemeApiKeyRequest req = new ThemeApiKeyRequest();
        req.setName("approved-key");
        assertThrows(BusinessException.class, () -> themeApiKeyService.create(theme.getId(), req));

        TestAuth.login(admin2.getId(), admin2.getUsername(), UserRole.API_EDITOR);
        Long taskId = pendingTaskForAssignee(admin2.getId(), admin2.getUsername());
        ApproveTaskResult outcome = approvalService.approveTask(taskId, true, "ok");

        assertTrue(outcome.isThemeKeyPickupPending());
        assertNull(outcome.getApiKey());
        assertEquals(1, consumerRepository.countByThemeId(theme.getId()));
        Long consumerId = consumerRepository.findAllByThemeIdOrderByCreatedAtAsc(theme.getId()).getFirst().getId();
        assertTrue(pickupRepository.existsByConsumerId(consumerId));

        TestAuth.login(admin1.getId(), admin1.getUsername(), UserRole.API_EDITOR);
        var claimed = themeApiKeyService.claimPickup(theme.getId(), consumerId);
        assertNotNull(claimed.getApiKey());
        assertFalse(pickupRepository.existsByConsumerId(consumerId));
    }

    @Test
    void nonSubmitterCannotClaim() {
        TestAuth.login(admin1.getId(), admin1.getUsername(), UserRole.API_EDITOR);
        ThemeApiKeyRequest req = new ThemeApiKeyRequest();
        req.setName("submitter-only");
        assertThrows(BusinessException.class, () -> themeApiKeyService.create(theme.getId(), req));

        TestAuth.login(admin2.getId(), admin2.getUsername(), UserRole.API_EDITOR);
        Long taskId = pendingTaskForAssignee(admin2.getId(), admin2.getUsername());
        approvalService.approveTask(taskId, true, "ok");

        Long consumerId = consumerRepository.findAllByThemeIdOrderByCreatedAtAsc(theme.getId()).getFirst().getId();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> themeApiKeyService.claimPickup(theme.getId(), consumerId));
        assertEquals(403, ex.getCode());
    }

    @Test
    void maxFiveKeysEnforced() {
        TestAuth.login(fixtures.requireSuperAdmin().getId(), "testadmin", UserRole.SUPER_ADMIN);
        for (int i = 0; i < ThemeApiKeyService.MAX_KEYS_PER_THEME; i++) {
            ThemeApiKeyRequest req = new ThemeApiKeyRequest();
            req.setName("key-" + i);
            themeApiKeyService.createDirect(theme.getId(), req);
        }
        ThemeApiKeyRequest req = new ThemeApiKeyRequest();
        req.setName("overflow");
        assertThrows(BusinessException.class, () -> themeApiKeyService.createDirect(theme.getId(), req));
    }

    private Long pendingTaskForAssignee(Long assigneeId, String username) {
        TestAuth.login(assigneeId, username, UserRole.API_EDITOR);
        List<Map<String, Object>> tasks = approvalService.listMyPendingTasks();
        assertFalse(tasks.isEmpty(), "assignee " + assigneeId + " should have pending task");
        return ((Number) tasks.get(0).get("taskId")).longValue();
    }
}
