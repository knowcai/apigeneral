package com.apigateway.service;

import com.apigateway.dto.*;
import com.apigateway.entity.*;
import com.apigateway.exception.BusinessException;
import com.apigateway.repository.*;
import com.apigateway.security.AuthzService;
import com.apigateway.security.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ApprovalService {

    private final ApprovalRequestRepository requestRepository;
    private final ApprovalTaskRepository taskRepository;
    private final ThemeMembershipRepository membershipRepository;
    private final ThemeService themeService;
    private final ApprovalApplyService applyService;
    private final AuthzService authzService;
    private final CurrentUser currentUser;
    private final SysUserRepository userRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final ApprovalDiffService approvalDiffService;
    private final ApprovalPayloadSanitizer payloadSanitizer;
    private final @Lazy ThemeApiKeyService themeApiKeyService;

    public ApprovalService(
            ApprovalRequestRepository requestRepository,
            ApprovalTaskRepository taskRepository,
            ThemeMembershipRepository membershipRepository,
            ThemeService themeService,
            @Lazy ApprovalApplyService applyService,
            AuthzService authzService,
            CurrentUser currentUser,
            SysUserRepository userRepository,
            AuditLogService auditLogService,
            ObjectMapper objectMapper,
            ApprovalDiffService approvalDiffService,
            ApprovalPayloadSanitizer payloadSanitizer,
            @Lazy ThemeApiKeyService themeApiKeyService) {
        this.requestRepository = requestRepository;
        this.taskRepository = taskRepository;
        this.membershipRepository = membershipRepository;
        this.themeService = themeService;
        this.applyService = applyService;
        this.authzService = authzService;
        this.currentUser = currentUser;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        this.approvalDiffService = approvalDiffService;
        this.payloadSanitizer = payloadSanitizer;
        this.themeApiKeyService = themeApiKeyService;
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public ApprovalRequest submit(ApprovalResourceType resourceType, Long resourceId, ApprovalAction action,
                                  Long themeId, String title, Object payload) {
        themeService.requireThemeWrite(themeId);
        if (authzService.isSuperAdmin()) {
            return applyDirect(resourceType, resourceId, action, themeId, title, payload);
        }
        validateApprovers(themeId, currentUser.requireUser().getId());
        ensureNoPendingDuplicate(resourceType, resourceId, action, themeId, payload);
        try {
            Object storedPayload = payloadSanitizer.sanitizeForStorage(resourceType, payload);
            ApprovalRequest req = new ApprovalRequest();
            req.setThemeId(themeId);
            req.setResourceType(resourceType);
            req.setResourceId(resourceId);
            req.setAction(action);
            req.setTitle(title);
            req.setPayload(objectMapper.writeValueAsString(storedPayload));
            req.setSubmitterId(currentUser.requireUser().getId());
            req.setCurrentStepOrder(1);
            ApprovalRequest saved = requestRepository.save(req);
            createAdminTasks(saved);
            auditLogService.log("SUBMIT", "APPROVAL", String.valueOf(saved.getId()), title, null);
            return saved;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("提交审批失败: " + e.getMessage());
        }
    }

    private ApprovalRequest applyDirect(ApprovalResourceType resourceType, Long resourceId, ApprovalAction action,
                                        Long themeId, String title, Object payload) {
        applyService.apply(resourceType, resourceId, action, themeId, payload);
        ApprovalRequest req = new ApprovalRequest();
        req.setThemeId(themeId);
        req.setResourceType(resourceType);
        req.setResourceId(resourceId);
        req.setAction(action);
        req.setTitle(title);
        req.setStatus(ApprovalStatus.APPROVED);
        req.setSubmitterId(currentUser.requireUser().getId());
        req.setResolvedAt(LocalDateTime.now());
        try {
            Object storedPayload = payloadSanitizer.sanitizeForStorage(resourceType, payload);
            req.setPayload(objectMapper.writeValueAsString(storedPayload));
        } catch (Exception ignored) {
            req.setPayload("{}");
        }
        return requestRepository.save(req);
    }

    private void validateApprovers(Long themeId, Long submitterId) {
        if (listApproverIds(themeId, submitterId).isEmpty()) {
            throw new BusinessException("无法提交：无可用审批人，请联系超级管理员");
        }
    }

    /** 其他主题管理员 + 全部超管均可审批（任一通过即生效）。 */
    private List<Long> listApproverIds(Long themeId, Long submitterId) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        membershipRepository.findByThemeId(themeId).stream()
                .filter(m -> m.getRole() == ThemeMembershipRole.THEME_ADMIN)
                .map(ThemeMembership::getUserId)
                .filter(uid -> !uid.equals(submitterId))
                .forEach(ids::add);
        userRepository.findByRoleAndEnabled(UserRole.SUPER_ADMIN, true).stream()
                .map(SysUser::getId)
                .forEach(ids::add);
        return List.copyOf(ids);
    }

    private void createAdminTasks(ApprovalRequest request) {
        List<Long> approverIds = listApproverIds(request.getThemeId(), request.getSubmitterId());
        if (approverIds.isEmpty()) {
            throw new BusinessException("无法提交：无可用审批人");
        }
        int sort = 0;
        for (Long approverId : approverIds) {
            ApprovalTask t = new ApprovalTask();
            t.setRequestId(request.getId());
            t.setStepOrder(1);
            t.setAssigneeId(approverId);
            t.setSortOrder(sort++);
            t.setStatus(ApprovalStatus.PENDING);
            taskRepository.save(t);
        }
    }

    @Transactional
    public ApproveTaskResult approveTask(Long taskId, boolean approved, String comment) {
        ApprovalTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException("审批任务不存在"));
        ApprovalRequest request = requestRepository.findById(task.getRequestId())
                .orElseThrow(() -> new BusinessException("审批单不存在"));
        syncApproverTasks(request);
        Long uid = currentUser.requireUser().getId();
        if (!canApproveTask(task)) {
            if (isEligibleApprover(request, uid)) {
                task = taskRepository.findByRequestIdOrderByStepOrderAscSortOrderAsc(request.getId()).stream()
                        .filter(t -> t.getAssigneeId().equals(uid) && t.getStatus() == ApprovalStatus.PENDING)
                        .findFirst()
                        .orElseThrow(() -> new BusinessException(403, "无权审批该任务"));
            } else {
                throw new BusinessException(403, "无权审批该任务");
            }
        }
        if (task.getStatus() != ApprovalStatus.PENDING) {
            throw new BusinessException("该任务已处理");
        }
        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw new BusinessException("审批单已结束");
        }
        if (!authzService.isSuperAdmin() && task.getAssigneeId().equals(request.getSubmitterId())) {
            throw new BusinessException("不能审批自己提交的变更");
        }

        task.setStatus(approved ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED);
        task.setComment(comment);
        task.setActedAt(LocalDateTime.now());
        taskRepository.save(task);

        if (!approved) {
            cancelPendingTasks(request.getId(), null);
            request.setStatus(ApprovalStatus.REJECTED);
            request.setResolvedAt(LocalDateTime.now());
            requestRepository.save(request);
            auditLogService.log("REJECT", "APPROVAL", String.valueOf(request.getId()), request.getTitle(), comment);
            return ApproveTaskResult.builder().build();
        }

        ApproveTaskResult outcome = finishApproved(request);
        cancelPendingTasks(request.getId(), task.getId());
        return outcome;
    }

    private boolean canApproveTask(ApprovalTask task) {
        Long uid = currentUser.requireUser().getId();
        if (authzService.isSuperAdmin()) {
            return true;
        }
        if (!task.getAssigneeId().equals(uid)) {
            return false;
        }
        ApprovalRequest request = requestRepository.findById(task.getRequestId()).orElse(null);
        if (request == null) {
            return false;
        }
        return isEligibleApprover(request, uid);
    }

    /** 提交后可新增主题管理员；按当前角色动态补齐待办，避免「后设管理员无法审批」。 */
    private void syncApproverTasks(ApprovalRequest request) {
        if (request.getStatus() != ApprovalStatus.PENDING) {
            return;
        }
        List<Long> approverIds = listApproverIds(request.getThemeId(), request.getSubmitterId());
        List<ApprovalTask> existing = taskRepository.findByRequestIdOrderByStepOrderAscSortOrderAsc(request.getId());
        Set<Long> existingAssignees = existing.stream()
                .map(ApprovalTask::getAssigneeId)
                .collect(Collectors.toSet());
        int maxSort = existing.stream().mapToInt(ApprovalTask::getSortOrder).max().orElse(-1);
        for (Long approverId : approverIds) {
            if (existingAssignees.contains(approverId)) {
                continue;
            }
            ApprovalTask t = new ApprovalTask();
            t.setRequestId(request.getId());
            t.setStepOrder(1);
            t.setAssigneeId(approverId);
            t.setSortOrder(++maxSort);
            t.setStatus(ApprovalStatus.PENDING);
            taskRepository.save(t);
        }
    }

    private void syncPendingTasksForUser(Long userId) {
        for (ApprovalRequest r : requestRepository.findByStatusOrderByCreatedAtDesc(ApprovalStatus.PENDING)) {
            if (isEligibleApprover(r, userId)) {
                syncApproverTasks(r);
            }
        }
    }

    private boolean isEligibleApprover(ApprovalRequest request, Long userId) {
        if (request.getSubmitterId().equals(userId)) {
            return false;
        }
        if (userRepository.findById(userId).map(u -> u.getRole() == UserRole.SUPER_ADMIN).orElse(false)) {
            return true;
        }
        return themeService.isThemeAdminMember(request.getThemeId(), userId);
    }

    private void cancelPendingTasks(Long requestId, Long exceptTaskId) {
        for (ApprovalTask t : taskRepository.findByRequestIdOrderByStepOrderAscSortOrderAsc(requestId)) {
            if (t.getStatus() == ApprovalStatus.PENDING && (exceptTaskId == null || !t.getId().equals(exceptTaskId))) {
                t.setStatus(ApprovalStatus.CANCELLED);
                taskRepository.save(t);
            }
        }
    }

    private ApproveTaskResult finishApproved(ApprovalRequest request) {
        ApprovalRequest locked = requestRepository.findByIdForUpdate(request.getId())
                .orElseThrow(() -> new BusinessException("审批单不存在"));
        if (locked.getStatus() != ApprovalStatus.PENDING) {
            throw new BusinessException("审批单已结束");
        }
        try {
            Object payload = objectMapper.readValue(locked.getPayload(), Object.class);
            payload = payloadSanitizer.restoreForApply(locked.getResourceType(), payload);
            Optional<String> revealedKey = applyService.apply(locked.getResourceType(), locked.getResourceId(),
                    locked.getAction(), locked.getThemeId(), payload,
                    locked.getSubmitterId(), locked.getId());
            locked.setStatus(ApprovalStatus.APPROVED);
            locked.setResolvedAt(LocalDateTime.now());
            requestRepository.save(locked);
            auditLogService.log("APPROVE", "APPROVAL", String.valueOf(locked.getId()), locked.getTitle(), null);
            if (locked.getResourceType() == ApprovalResourceType.THEME_API_KEY
                    && locked.getAction() == ApprovalAction.CREATE) {
                return ApproveTaskResult.builder().themeKeyPickupPending(true).build();
            }
            return ApproveTaskResult.builder().apiKey(revealedKey.orElse(null)).build();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("审批通过后应用变更失败: " + e.getMessage());
        }
    }

    @Transactional
    public void revokePendingTasksForRemovedThemeAdmin(Long themeId, Long userId) {
        for (ApprovalRequest r : requestRepository.findByThemeIdAndStatusOrderByCreatedAtDesc(
                themeId, ApprovalStatus.PENDING)) {
            for (ApprovalTask t : taskRepository.findByRequestIdOrderByStepOrderAscSortOrderAsc(r.getId())) {
                if (t.getAssigneeId().equals(userId) && t.getStatus() == ApprovalStatus.PENDING) {
                    t.setStatus(ApprovalStatus.CANCELLED);
                    taskRepository.save(t);
                }
            }
        }
    }

    @Transactional
    public long countMyPendingTasks() {
        Long uid = currentUser.requireUser().getId();
        syncPendingTasksForUser(uid);
        return selectPendingTasksForUser(uid).size();
    }

    @Transactional
    public void withdrawRequest(Long requestId) {
        ApprovalRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException("审批单不存在"));
        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw new BusinessException("审批单已结束，无法撤回");
        }
        Long uid = currentUser.requireUser().getId();
        if (!request.getSubmitterId().equals(uid) && !authzService.isSuperAdmin()) {
            throw new BusinessException(403, "仅提交人可撤回该审批");
        }
        cancelPendingTasks(request.getId(), null);
        request.setStatus(ApprovalStatus.CANCELLED);
        request.setResolvedAt(LocalDateTime.now());
        requestRepository.save(request);
        auditLogService.log("WITHDRAW", "APPROVAL", String.valueOf(request.getId()), request.getTitle(), null);
    }

    private void ensureNoPendingDuplicate(ApprovalResourceType resourceType, Long resourceId,
                                          ApprovalAction action, Long themeId, Object payload) {
        if (resourceId != null) {
            if (requestRepository.existsByResourceTypeAndResourceIdAndStatus(
                    resourceType, resourceId, ApprovalStatus.PENDING)) {
                throw new BusinessException("该资源已有待审批变更，请等待处理完成后再提交");
            }
        }
        if (resourceType == ApprovalResourceType.THEME_API_KEY && action == ApprovalAction.CREATE) {
            themeApiKeyService.ensureSlotAvailable(themeId);
        }
        if (resourceId != null) {
            return;
        }
        if (action != ApprovalAction.CREATE) {
            return;
        }
        List<ApprovalRequest> pending = requestRepository.findByThemeIdAndResourceTypeAndStatus(
                themeId, resourceType, ApprovalStatus.PENDING);
        for (ApprovalRequest existing : pending) {
            if (existing.getAction() != ApprovalAction.CREATE) {
                continue;
            }
            try {
                if (resourceType == ApprovalResourceType.API_DEFINITION) {
                    ApiDefinitionRequest existingReq = objectMapper.readValue(existing.getPayload(), ApiDefinitionRequest.class);
                    ApiDefinitionRequest newReq = objectMapper.convertValue(payload, ApiDefinitionRequest.class);
                    if (existingReq.getApiCode() != null && existingReq.getApiCode().equals(newReq.getApiCode())) {
                        throw new BusinessException("该 API 编码已有待审批创建单");
                    }
                } else if (resourceType == ApprovalResourceType.DATASOURCE) {
                    DatasourceRequest existingReq = objectMapper.readValue(existing.getPayload(), DatasourceRequest.class);
                    DatasourceRequest newReq = objectMapper.convertValue(payload, DatasourceRequest.class);
                    if (existingReq.getName() != null && existingReq.getName().equals(newReq.getName())) {
                        throw new BusinessException("该数据源名称已有待审批创建单");
                    }
                }
            } catch (BusinessException e) {
                throw e;
            } catch (Exception ignored) {
                // skip malformed payload
            }
        }
    }

    @Transactional
    public List<Map<String, Object>> listMyPendingTasks() {
        Long uid = currentUser.requireUser().getId();
        syncPendingTasksForUser(uid);
        return selectPendingTasksForUser(uid).stream()
                .map(this::taskView)
                .collect(Collectors.toList());
    }

    /** 同一审批单可能有多条待办（超管 + 各主题管理员）；展示时按 request 去重。 */
    private List<ApprovalTask> selectPendingTasksForUser(Long uid) {
        List<ApprovalTask> source = authzService.isSuperAdmin()
                ? taskRepository.findByStatusOrderByIdDesc(ApprovalStatus.PENDING)
                : taskRepository.findByAssigneeIdAndStatusOrderByIdDesc(uid, ApprovalStatus.PENDING);
        Map<Long, ApprovalTask> byRequest = new LinkedHashMap<>();
        for (ApprovalTask t : source) {
            if (t.getStatus() != ApprovalStatus.PENDING) {
                continue;
            }
            ApprovalRequest r = requestRepository.findById(t.getRequestId()).orElse(null);
            if (r == null || r.getStatus() != ApprovalStatus.PENDING || !isEligibleApprover(r, uid)) {
                continue;
            }
            byRequest.merge(t.getRequestId(), t, (a, b) -> preferTaskForUser(uid, a, b));
        }
        return new ArrayList<>(byRequest.values());
    }

    private ApprovalTask preferTaskForUser(Long uid, ApprovalTask a, ApprovalTask b) {
        if (a.getAssigneeId().equals(uid)) {
            return a;
        }
        if (b.getAssigneeId().equals(uid)) {
            return b;
        }
        return a;
    }

    public List<Map<String, Object>> listPendingRequests() {
        authzService.requireAuthenticated();
        List<ApprovalRequest> list = requestRepository.findByStatusOrderByCreatedAtDesc(ApprovalStatus.PENDING);
        if (!authzService.isSuperAdmin()) {
            Long uid = currentUser.requireUser().getId();
            list = list.stream().filter(r -> canViewRequest(r, uid)).collect(Collectors.toList());
        }
        return list.stream().map(this::requestView).collect(Collectors.toList());
    }

    public List<Map<String, Object>> listHistory(int limit) {
        authzService.requireAuthenticated();
        int effectiveLimit = Math.min(Math.max(limit, 1), 200);
        Page<ApprovalRequest> page = requestRepository.findByStatusInOrderByResolvedAtDesc(
                List.of(ApprovalStatus.APPROVED, ApprovalStatus.REJECTED),
                PageRequest.of(0, effectiveLimit));
        List<ApprovalRequest> list = page.getContent();
        if (!authzService.isSuperAdmin()) {
            Long uid = currentUser.requireUser().getId();
            list = list.stream().filter(r -> canViewRequest(r, uid)).collect(Collectors.toList());
        }
        return list.stream().map(this::historyView).collect(Collectors.toList());
    }

    private boolean canViewRequest(ApprovalRequest r, Long uid) {
        if (r.getSubmitterId().equals(uid)) {
            return true;
        }
        if (themeService.isThemeAdmin(r.getThemeId(), uid)) {
            return true;
        }
        return taskRepository.findByRequestIdOrderByStepOrderAscSortOrderAsc(r.getId()).stream()
                .anyMatch(t -> t.getAssigneeId().equals(uid));
    }

    private Map<String, Object> taskView(ApprovalTask t) {
        ApprovalRequest r = requestRepository.findById(t.getRequestId()).orElse(null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("taskId", t.getId());
        m.put("requestId", t.getRequestId());
        m.put("status", t.getStatus());
        if (r != null) {
            m.put("title", r.getTitle());
            m.put("resourceType", r.getResourceType());
            m.put("action", r.getAction());
            m.put("themeId", r.getThemeId());
            m.put("resourceId", r.getResourceId());
            m.put("payload", payloadSanitizer.redactForDisplay(r.getPayload()));
            attachDiff(m, r);
            m.put("submitterId", r.getSubmitterId());
            userRepository.findById(r.getSubmitterId()).ifPresent(u -> m.put("submitterName", u.getDisplayName()));
        }
        return m;
    }

    private Map<String, Object> requestView(ApprovalRequest r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("title", r.getTitle());
        m.put("themeId", r.getThemeId());
        m.put("resourceType", r.getResourceType());
        m.put("action", r.getAction());
        m.put("resourceId", r.getResourceId());
        m.put("status", r.getStatus());
        m.put("payload", payloadSanitizer.redactForDisplay(r.getPayload()));
        attachDiff(m, r);
        m.put("createdAt", r.getCreatedAt());
        m.put("submitterId", r.getSubmitterId());
        userRepository.findById(r.getSubmitterId()).ifPresent(u -> m.put("submitterName", u.getDisplayName()));
        return m;
    }

    private Map<String, Object> historyView(ApprovalRequest r) {
        Map<String, Object> m = requestView(r);
        m.put("resolvedAt", r.getResolvedAt());
        attachResolver(r, m);
        return m;
    }

    /** 已通过/已驳回单：填充实际审批人（含超管直生效场景）。 */
    private void attachResolver(ApprovalRequest r, Map<String, Object> m) {
        if (r.getStatus() != ApprovalStatus.APPROVED && r.getStatus() != ApprovalStatus.REJECTED) {
            return;
        }
        Optional<ApprovalTask> acted = taskRepository.findByRequestIdOrderByStepOrderAscSortOrderAsc(r.getId())
                .stream()
                .filter(t -> t.getStatus() == ApprovalStatus.APPROVED || t.getStatus() == ApprovalStatus.REJECTED)
                .filter(t -> t.getActedAt() != null)
                .max(Comparator.comparing(ApprovalTask::getActedAt));
        if (acted.isPresent()) {
            ApprovalTask task = acted.get();
            m.put("approverId", task.getAssigneeId());
            userRepository.findById(task.getAssigneeId()).ifPresent(u ->
                    m.put("approverName", displayName(u)));
            if (task.getComment() != null && !task.getComment().isBlank()) {
                m.put("approverComment", task.getComment());
            }
            return;
        }
        if (r.getStatus() == ApprovalStatus.APPROVED) {
            m.put("directApply", true);
            userRepository.findById(r.getSubmitterId()).ifPresent(u ->
                    m.put("approverName", displayName(u)));
        }
    }

    private String displayName(SysUser u) {
        if (u.getDisplayName() != null && !u.getDisplayName().isBlank()) {
            return u.getDisplayName();
        }
        return u.getUsername();
    }

    private void attachDiff(Map<String, Object> target, ApprovalRequest r) {
        ApprovalDiffResult diffResult = approvalDiffService.buildDiffResult(
                r.getResourceType(), r.getAction(), r.getResourceId(), r.getPayload());
        target.put("diff", diffResult.getDiff());
        if (diffResult.getError() != null) {
            target.put("diffError", diffResult.getError());
        }
    }
}
