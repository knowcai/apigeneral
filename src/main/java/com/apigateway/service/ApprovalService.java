package com.apigateway.service;

import com.apigateway.dto.*;
import com.apigateway.entity.*;
import com.apigateway.exception.BusinessException;
import com.apigateway.repository.*;
import com.apigateway.security.AuthzService;
import com.apigateway.security.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Lazy;
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
            ObjectMapper objectMapper) {
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
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public ApprovalRequest submit(ApprovalResourceType resourceType, Long resourceId, ApprovalAction action,
                                  Long themeId, String title, Object payload) {
        themeService.requireThemeWrite(themeId);
        if (authzService.isSuperAdmin()) {
            return applyDirect(resourceType, resourceId, action, themeId, title, payload);
        }
        validateApprovers(themeId, currentUser.requireUser().getId());
        validateSqlInPayload(payload);
        try {
            ApprovalRequest req = new ApprovalRequest();
            req.setThemeId(themeId);
            req.setResourceType(resourceType);
            req.setResourceId(resourceId);
            req.setAction(action);
            req.setTitle(title);
            req.setPayload(objectMapper.writeValueAsString(payload));
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
            req.setPayload(objectMapper.writeValueAsString(payload));
        } catch (Exception ignored) {
            req.setPayload("{}");
        }
        return requestRepository.save(req);
    }

    private void validateApprovers(Long themeId, Long submitterId) {
        if (listApproverIds(themeId, submitterId).isEmpty()) {
            throw new BusinessException("无法提交：无其他主题管理员可审批，请联系超级管理员");
        }
    }

    private List<Long> listApproverIds(Long themeId, Long submitterId) {
        return membershipRepository.findByThemeId(themeId).stream()
                .filter(m -> m.getRole() == ThemeMembershipRole.THEME_ADMIN)
                .map(ThemeMembership::getUserId)
                .filter(uid -> !uid.equals(submitterId))
                .toList();
    }

    private void validateSqlInPayload(Object payload) {
        if (payload instanceof ApiVersionRequest ver) {
            SqlSecurityValidator.validateReadOnlySql(ver.getSqlTemplate());
            return;
        }
        if (payload instanceof Map<?, ?> map) {
            if (map.get("sqlTemplate") instanceof String sql) {
                SqlSecurityValidator.validateReadOnlySql(sql);
            }
            Object version = map.get("version");
            if (version instanceof ApiVersionRequest verReq) {
                SqlSecurityValidator.validateReadOnlySql(verReq.getSqlTemplate());
            } else if (version instanceof Map<?, ?> versionMap && versionMap.get("sqlTemplate") instanceof String nestedSql) {
                SqlSecurityValidator.validateReadOnlySql(nestedSql);
            }
        }
    }

    private void createAdminTasks(ApprovalRequest request) {
        List<Long> approverIds = listApproverIds(request.getThemeId(), request.getSubmitterId());
        if (approverIds.isEmpty()) {
            throw new BusinessException("无法提交：无其他主题管理员可审批");
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
    public void approveTask(Long taskId, boolean approved, String comment) {
        ApprovalTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException("审批任务不存在"));
        if (!canApproveTask(task)) {
            throw new BusinessException(403, "无权审批该任务");
        }
        if (task.getStatus() != ApprovalStatus.PENDING) {
            throw new BusinessException("该任务已处理");
        }
        ApprovalRequest request = requestRepository.findById(task.getRequestId())
                .orElseThrow(() -> new BusinessException("审批单不存在"));
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
            return;
        }

        finishApproved(request);
        cancelPendingTasks(request.getId(), task.getId());
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
        return themeService.isThemeAdmin(request.getThemeId(), uid);
    }

    private void cancelPendingTasks(Long requestId, Long exceptTaskId) {
        for (ApprovalTask t : taskRepository.findByRequestIdOrderByStepOrderAscSortOrderAsc(requestId)) {
            if (t.getStatus() == ApprovalStatus.PENDING && (exceptTaskId == null || !t.getId().equals(exceptTaskId))) {
                t.setStatus(ApprovalStatus.CANCELLED);
                taskRepository.save(t);
            }
        }
    }

    private void finishApproved(ApprovalRequest request) {
        try {
            Object payload = objectMapper.readValue(request.getPayload(), Object.class);
            applyService.apply(request.getResourceType(), request.getResourceId(), request.getAction(),
                    request.getThemeId(), payload);
            request.setStatus(ApprovalStatus.APPROVED);
            request.setResolvedAt(LocalDateTime.now());
            requestRepository.save(request);
            auditLogService.log("APPROVE", "APPROVAL", String.valueOf(request.getId()), request.getTitle(), null);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("审批通过后应用变更失败: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> listMyPendingTasks() {
        if (authzService.isSuperAdmin()) {
            return taskRepository.findByStatusOrderByIdDesc(ApprovalStatus.PENDING).stream()
                    .map(this::taskView)
                    .collect(Collectors.toList());
        }
        Long uid = currentUser.requireUser().getId();
        return taskRepository.findByAssigneeIdAndStatusOrderByIdDesc(uid, ApprovalStatus.PENDING).stream()
                .filter(t -> {
                    ApprovalRequest r = requestRepository.findById(t.getRequestId()).orElse(null);
                    return r != null && themeService.isThemeAdmin(r.getThemeId(), uid);
                })
                .map(this::taskView)
                .collect(Collectors.toList());
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
            m.put("payload", r.getPayload());
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
        m.put("status", r.getStatus());
        m.put("payload", r.getPayload());
        m.put("createdAt", r.getCreatedAt());
        userRepository.findById(r.getSubmitterId()).ifPresent(u -> m.put("submitterName", u.getDisplayName()));
        return m;
    }
}
