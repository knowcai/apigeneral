package com.apigateway.service;

import com.apigateway.dto.ApiDefinitionRequest;
import com.apigateway.dto.ThemeApiKeyRequest;
import com.apigateway.dto.ThemeMemberRequest;
import com.apigateway.dto.ThemeRequest;
import com.apigateway.dto.ThemeResponse;
import com.apigateway.entity.*;
import com.apigateway.exception.BusinessException;
import com.apigateway.repository.ApiDefinitionRepository;
import com.apigateway.repository.ApprovalRequestRepository;
import com.apigateway.repository.ApprovalTaskRepository;
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
class ApprovalFlowIntegrationTest {

    @Autowired
    private ApprovalService approvalService;
    @Autowired
    private TestFixtures fixtures;
    @Autowired
    private ApiDefinitionRepository apiDefinitionRepository;
    @Autowired
    private ApprovalRequestRepository approvalRequestRepository;
    @Autowired
    private ApprovalTaskRepository approvalTaskRepository;
    @Autowired
    private ApiManagementService apiManagementService;
    @Autowired
    private GatewayTestFixtures gatewayFixtures;

    @Autowired
    private ThemeApiKeyService themeApiKeyService;

    @Autowired
    private ThemeService themeService;

    private SysUser admin1;
    private SysUser admin2;
    private SysUser member;
    private ThemeResponse theme;

    @BeforeEach
    void setUp() {
        admin1 = fixtures.createUser("appr_admin1", UserRole.API_EDITOR);
        admin2 = fixtures.createUser("appr_admin2", UserRole.API_EDITOR);
        member = fixtures.createUser("appr_member", UserRole.API_EDITOR);

        theme = fixtures.createThemeWithAdmins("审批测试主题", List.of(admin1.getId(), admin2.getId()));
        fixtures.assignMembers(theme.getId(), List.of(member.getId()));
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    @Test
    void singleThemeAdminApprovalCreatesApi() {
        ApiDefinitionRequest payload = apiPayload("simple-api", theme.getId());

        TestAuth.login(member.getId(), member.getUsername(), UserRole.API_EDITOR);
        ApprovalRequest submitted = approvalService.submit(
                ApprovalResourceType.API_DEFINITION, null, ApprovalAction.CREATE,
                theme.getId(), "新建 API", payload);
        assertEquals(ApprovalStatus.PENDING, submitted.getStatus());

        Long taskId = pendingTaskForAssignee(admin1.getId(), admin1.getUsername());
        TestAuth.login(admin1.getId(), admin1.getUsername(), UserRole.API_EDITOR);
        approvalService.approveTask(taskId, true, "ok");

        ApprovalRequest finished = approvalRequestRepository.findById(submitted.getId()).orElseThrow();
        assertEquals(ApprovalStatus.APPROVED, finished.getStatus());
        assertTrue(apiDefinitionRepository.findByApiCode("simple-api").isPresent());
    }

    @Test
    void oneAdminApproveIsEnoughWhenMultipleTasksExist() {
        TestAuth.login(member.getId(), member.getUsername(), UserRole.API_EDITOR);
        ApprovalRequest submitted = approvalService.submit(
                ApprovalResourceType.API_DEFINITION, null, ApprovalAction.CREATE,
                theme.getId(), "任一管理员审批", apiPayload("either-api", theme.getId()));

        TestAuth.login(admin2.getId(), admin2.getUsername(), UserRole.API_EDITOR);
        approvalService.approveTask(pendingTaskForAssignee(admin2.getId(), admin2.getUsername()), true, "ok");

        assertEquals(ApprovalStatus.APPROVED,
                approvalRequestRepository.findById(submitted.getId()).orElseThrow().getStatus());
        assertTrue(apiDefinitionRepository.findByApiCode("either-api").isPresent());
    }

    @Test
    void rejectStopsApproval() {
        TestAuth.login(member.getId(), member.getUsername(), UserRole.API_EDITOR);
        ApprovalRequest submitted = approvalService.submit(
                ApprovalResourceType.API_DEFINITION, null, ApprovalAction.CREATE,
                theme.getId(), "驳回测试", apiPayload("reject-api", theme.getId()));

        TestAuth.login(admin1.getId(), admin1.getUsername(), UserRole.API_EDITOR);
        approvalService.approveTask(pendingTaskForAssignee(admin1.getId(), admin1.getUsername()), false, "不同意");

        ApprovalRequest finished = approvalRequestRepository.findById(submitted.getId()).orElseThrow();
        assertEquals(ApprovalStatus.REJECTED, finished.getStatus());
        assertTrue(apiDefinitionRepository.findByApiCode("reject-api").isEmpty());
    }

    @Test
    void cannotApproveWithoutPermission() {
        TestAuth.login(member.getId(), member.getUsername(), UserRole.API_EDITOR);
        approvalService.submit(
                ApprovalResourceType.API_DEFINITION, null, ApprovalAction.CREATE,
                theme.getId(), "越权审批", apiPayload("forbidden-api", theme.getId()));

        Long taskId = pendingTaskForAssignee(admin1.getId(), admin1.getUsername());
        TestAuth.login(member.getId(), member.getUsername(), UserRole.API_EDITOR);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> approvalService.approveTask(taskId, true, null));
        assertEquals(403, ex.getCode());
    }

    @Test
    void multipleThemeAdminsAlsoAssignTasksToSuperAdmin() {
        TestAuth.login(member.getId(), member.getUsername(), UserRole.API_EDITOR);
        ApprovalRequest submitted = approvalService.submit(
                ApprovalResourceType.API_DEFINITION, null, ApprovalAction.CREATE,
                theme.getId(), "超管也可审", apiPayload("super-can-approve-api", theme.getId()));

        SysUser superAdmin = fixtures.requireSuperAdmin();
        TestAuth.login(superAdmin.getId(), superAdmin.getUsername(), UserRole.SUPER_ADMIN);
        List<Map<String, Object>> tasks = approvalService.listMyPendingTasks();
        assertTrue(tasks.stream().anyMatch(t -> submitted.getId().equals(((Number) t.get("requestId")).longValue())));

        Long taskId = tasks.stream()
                .filter(t -> submitted.getId().equals(((Number) t.get("requestId")).longValue()))
                .map(t -> ((Number) t.get("taskId")).longValue())
                .findFirst()
                .orElseThrow();
        approvalService.approveTask(taskId, true, "超管通过");

        assertEquals(ApprovalStatus.APPROVED,
                approvalRequestRepository.findById(submitted.getId()).orElseThrow().getStatus());
        assertTrue(apiDefinitionRepository.findByApiCode("super-can-approve-api").isPresent());
    }

    @Test
    void singleThemeAdminSubmissionUsesSuperAdminFallback() {
        SysUser loneAdmin = fixtures.createUser("lone_admin", UserRole.API_EDITOR);
        ThemeResponse loneTheme = fixtures.createThemeWithAdmins("单人主题", List.of(loneAdmin.getId()));

        TestAuth.login(loneAdmin.getId(), loneAdmin.getUsername(), UserRole.API_EDITOR);
        ApprovalRequest submitted = approvalService.submit(
                ApprovalResourceType.API_DEFINITION, null, ApprovalAction.CREATE,
                loneTheme.getId(), "单人主题提交", apiPayload("lone-api", loneTheme.getId()));
        assertEquals(ApprovalStatus.PENDING, submitted.getStatus());
    }

    @Test
    void themeAdminSubmissionNeedsAnotherAdmin() {
        TestAuth.login(admin1.getId(), admin1.getUsername(), UserRole.API_EDITOR);
        ApprovalRequest submitted = approvalService.submit(
                ApprovalResourceType.API_DEFINITION, null, ApprovalAction.CREATE,
                theme.getId(), "管理员提交", apiPayload("admin-submit-api", theme.getId()));
        assertEquals(ApprovalStatus.PENDING, submitted.getStatus());
        assertFalse(apiDefinitionRepository.findByApiCode("admin-submit-api").isPresent());
    }

    @Test
    void superAdminSubmitAppliesDirectly() {
        SysUser superAdmin = fixtures.requireSuperAdmin();
        TestAuth.login(superAdmin.getId(), superAdmin.getUsername(), UserRole.SUPER_ADMIN);
        ApprovalRequest result = approvalService.submit(
                ApprovalResourceType.API_DEFINITION, null, ApprovalAction.CREATE,
                theme.getId(), "超管直建", apiPayload("super-api", theme.getId()));
        assertEquals(ApprovalStatus.APPROVED, result.getStatus());
        assertTrue(apiDefinitionRepository.findByApiCode("super-api").isPresent());
    }

    @Test
    void superAdminCanApprovePendingTask() {
        TestAuth.login(member.getId(), member.getUsername(), UserRole.API_EDITOR);
        ApprovalRequest submitted = approvalService.submit(
                ApprovalResourceType.API_DEFINITION, null, ApprovalAction.CREATE,
                theme.getId(), "超管审批", apiPayload("super-approve-api", theme.getId()));

        SysUser superAdmin = fixtures.requireSuperAdmin();
        TestAuth.login(superAdmin.getId(), superAdmin.getUsername(), UserRole.SUPER_ADMIN);
        List<Map<String, Object>> tasks = approvalService.listMyPendingTasks();
        assertFalse(tasks.isEmpty());
        Long taskId = ((Number) tasks.get(0).get("taskId")).longValue();
        approvalService.approveTask(taskId, true, "超管通过");

        assertEquals(ApprovalStatus.APPROVED,
                approvalRequestRepository.findById(submitted.getId()).orElseThrow().getStatus());
        assertTrue(apiDefinitionRepository.findByApiCode("super-approve-api").isPresent());
    }

    @Test
    void deleteApiViaApproval() {
        var ds = gatewayFixtures.createDatasource(theme.getId(), "delete-ds");
        var published = gatewayFixtures.publishApi(theme.getId(), "delete-me", ds.getId());

        TestAuth.login(admin1.getId(), admin1.getUsername(), UserRole.API_EDITOR);
        BusinessException pending = assertThrows(BusinessException.class,
                () -> apiManagementService.deleteDefinition(published.definition().getId()));
        assertEquals(202, pending.getCode());

        Long taskId = pendingTaskForAssignee(admin2.getId(), admin2.getUsername());
        TestAuth.login(admin2.getId(), admin2.getUsername(), UserRole.API_EDITOR);
        approvalService.approveTask(taskId, true, "同意删除");

        assertTrue(apiDefinitionRepository.findById(published.definition().getId()).isEmpty());
    }

    @Test
    void secondApprovalOnFinishedRequestFails() {
        TestAuth.login(member.getId(), member.getUsername(), UserRole.API_EDITOR);
        ApprovalRequest submitted = approvalService.submit(
                ApprovalResourceType.API_DEFINITION, null, ApprovalAction.CREATE,
                theme.getId(), "并发防重", apiPayload("dup-approve-api", theme.getId()));

        var tasks = approvalTaskRepository.findByRequestIdOrderByStepOrderAscSortOrderAsc(submitted.getId());
        assertTrue(tasks.size() >= 2);
        Long task1 = tasks.get(0).getId();
        Long task2 = tasks.get(1).getId();

        TestAuth.login(admin1.getId(), admin1.getUsername(), UserRole.API_EDITOR);
        approvalService.approveTask(task1, true, "first");

        TestAuth.login(admin2.getId(), admin2.getUsername(), UserRole.API_EDITOR);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> approvalService.approveTask(task2, true, "second"));
        assertTrue(ex.getMessage().contains("已处理") || ex.getMessage().contains("已结束"));
        assertEquals(1, apiDefinitionRepository.findByApiCode("dup-approve-api").stream().count());
    }

    @Test
    void listHistoryIncludesApprovedThemeKeyDeleteAfterConsumerRemoved() {
        TestAuth.login(fixtures.requireSuperAdmin().getId(), "testadmin", UserRole.SUPER_ADMIN);
        ThemeApiKeyRequest keyReq = new ThemeApiKeyRequest();
        keyReq.setName("history-del-key");
        var created = themeApiKeyService.createDirect(theme.getId(), keyReq);
        Long keyId = created.getConsumer().getId();

        TestAuth.login(admin1.getId(), admin1.getUsername(), UserRole.API_EDITOR);
        assertThrows(BusinessException.class, () -> themeApiKeyService.deleteKey(theme.getId(), keyId));

        TestAuth.login(admin2.getId(), admin2.getUsername(), UserRole.API_EDITOR);
        approvalService.approveTask(pendingTaskForAssignee(admin2.getId(), admin2.getUsername()), true, "ok");

        TestAuth.login(fixtures.requireSuperAdmin().getId(), "testadmin", UserRole.SUPER_ADMIN);
        List<Map<String, Object>> history = approvalService.listHistory(50);
        assertFalse(history.isEmpty());
        assertTrue(history.stream().anyMatch(h ->
                "THEME_API_KEY".equals(String.valueOf(h.get("resourceType")))
                        && "DELETE".equals(String.valueOf(h.get("action")))
                        && "APPROVED".equals(String.valueOf(h.get("status")))
                        && admin2.getDisplayName().equals(h.get("approverName"))));
    }

    @Test
    void superAdminMyTasksDedupedByRequestWhenMultipleAssignees() {
        ThemeResponse loneTheme = fixtures.createThemeWithAdmins("去重测试主题", List.of(admin1.getId()));
        SysUser lateAdmin = fixtures.createUser("dedupe_admin", UserRole.API_EDITOR);

        TestAuth.login(admin1.getId(), admin1.getUsername(), UserRole.API_EDITOR);
        ApprovalRequest submitted = approvalService.submit(
                ApprovalResourceType.API_DEFINITION, null, ApprovalAction.CREATE,
                loneTheme.getId(), "去重测试", apiPayload("dedupe-api", loneTheme.getId()));

        TestAuth.login(fixtures.requireSuperAdmin().getId(), "testadmin", UserRole.SUPER_ADMIN);
        ThemeMemberRequest m = new ThemeMemberRequest();
        m.setUserId(lateAdmin.getId());
        m.setRole(ThemeMembershipRole.THEME_ADMIN);
        ThemeRequest update = new ThemeRequest();
        update.setName(loneTheme.getName());
        update.setEnabled(true);
        update.setMembers(List.of(themeAdminMember(admin1.getId()), m));
        themeService.update(loneTheme.getId(), update);

        SysUser superAdmin = fixtures.requireSuperAdmin();
        TestAuth.login(superAdmin.getId(), superAdmin.getUsername(), UserRole.SUPER_ADMIN);
        assertTrue(approvalTaskRepository.findByRequestIdOrderByStepOrderAscSortOrderAsc(submitted.getId()).stream()
                        .filter(t -> t.getStatus() == ApprovalStatus.PENDING)
                        .count() >= 2,
                "same request should have tasks for super admin and newly added theme admin");

        List<Map<String, Object>> tasks = approvalService.listMyPendingTasks();
        long forRequest = tasks.stream()
                .filter(t -> submitted.getId().equals(((Number) t.get("requestId")).longValue()))
                .count();
        assertEquals(1, forRequest);
        assertEquals(1, approvalService.countMyPendingTasks());
    }

    @Test
    void removedThemeAdminNoLongerSeesPendingTasks() {
        ThemeResponse loneTheme = fixtures.createThemeWithAdmins("撤职测试主题", List.of(admin1.getId(), admin2.getId()));

        TestAuth.login(admin1.getId(), admin1.getUsername(), UserRole.API_EDITOR);
        approvalService.submit(
                ApprovalResourceType.API_DEFINITION, null, ApprovalAction.CREATE,
                loneTheme.getId(), "撤职后不可审", apiPayload("revoke-admin-api", loneTheme.getId()));

        TestAuth.login(admin2.getId(), admin2.getUsername(), UserRole.API_EDITOR);
        assertEquals(1, approvalService.countMyPendingTasks());

        TestAuth.login(fixtures.requireSuperAdmin().getId(), "testadmin", UserRole.SUPER_ADMIN);
        ThemeRequest update = new ThemeRequest();
        update.setName(loneTheme.getName());
        update.setEnabled(true);
        update.setMembers(List.of(themeAdminMember(admin1.getId())));
        themeService.update(loneTheme.getId(), update);

        TestAuth.login(admin2.getId(), admin2.getUsername(), UserRole.API_EDITOR);
        assertEquals(0, approvalService.countMyPendingTasks());
        assertTrue(approvalService.listMyPendingTasks().isEmpty());
    }

    @Test
    void newlyAddedThemeAdminCanApprovePendingRequest() {
        ThemeResponse loneTheme = fixtures.createThemeWithAdmins("后加管理员主题", List.of(admin1.getId()));
        SysUser lateAdmin = fixtures.createUser("late_admin", UserRole.API_EDITOR);

        TestAuth.login(admin1.getId(), admin1.getUsername(), UserRole.API_EDITOR);
        ApprovalRequest submitted = approvalService.submit(
                ApprovalResourceType.API_DEFINITION, null, ApprovalAction.CREATE,
                loneTheme.getId(), "后加管理员审批", apiPayload("late-admin-api", loneTheme.getId()));
        assertEquals(ApprovalStatus.PENDING, submitted.getStatus());
        assertFalse(approvalTaskRepository.findByRequestIdOrderByStepOrderAscSortOrderAsc(submitted.getId()).stream()
                .anyMatch(t -> t.getAssigneeId().equals(lateAdmin.getId())));

        TestAuth.login(fixtures.requireSuperAdmin().getId(), "testadmin", UserRole.SUPER_ADMIN);
        ThemeMemberRequest m = new ThemeMemberRequest();
        m.setUserId(lateAdmin.getId());
        m.setRole(ThemeMembershipRole.THEME_ADMIN);
        ThemeRequest update = new ThemeRequest();
        update.setName(loneTheme.getName());
        update.setEnabled(true);
        update.setMembers(List.of(
                themeAdminMember(admin1.getId()),
                m));
        themeService.update(loneTheme.getId(), update);

        TestAuth.login(lateAdmin.getId(), lateAdmin.getUsername(), UserRole.API_EDITOR);
        List<Map<String, Object>> tasks = approvalService.listMyPendingTasks();
        assertTrue(tasks.stream().anyMatch(t ->
                submitted.getId().equals(((Number) t.get("requestId")).longValue())));
        Long taskId = tasks.stream()
                .filter(t -> submitted.getId().equals(((Number) t.get("requestId")).longValue()))
                .map(t -> ((Number) t.get("taskId")).longValue())
                .findFirst()
                .orElseThrow();
        approvalService.approveTask(taskId, true, "后设管理员通过");

        assertEquals(ApprovalStatus.APPROVED,
                approvalRequestRepository.findById(submitted.getId()).orElseThrow().getStatus());
        assertTrue(apiDefinitionRepository.findByApiCode("late-admin-api").isPresent());
    }

    private ThemeMemberRequest themeAdminMember(Long userId) {
        ThemeMemberRequest m = new ThemeMemberRequest();
        m.setUserId(userId);
        m.setRole(ThemeMembershipRole.THEME_ADMIN);
        return m;
    }

    private ApiDefinitionRequest apiPayload(String code, Long themeId) {
        ApiDefinitionRequest req = new ApiDefinitionRequest();
        req.setApiCode(code);
        req.setName(code);
        req.setThemeId(themeId);
        return req;
    }

    private Long pendingTaskForAssignee(Long assigneeId, String username) {
        TestAuth.login(assigneeId, username, UserRole.API_EDITOR);
        List<Map<String, Object>> tasks = approvalService.listMyPendingTasks();
        assertFalse(tasks.isEmpty(), "assignee " + assigneeId + " should have pending task");
        return ((Number) tasks.get(0).get("taskId")).longValue();
    }
}
