package com.apigateway.service;

import com.apigateway.dto.ApiDefinitionRequest;
import com.apigateway.dto.ApiVersionRequest;
import com.apigateway.entity.*;
import com.apigateway.exception.BusinessException;
import com.apigateway.repository.ApiDefinitionRepository;
import com.apigateway.repository.ApiVersionRepository;
import com.apigateway.repository.ThemeRepository;
import com.apigateway.security.AuthzService;
import com.apigateway.security.CurrentUser;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ApiManagementService {

    private final ApiDefinitionRepository definitionRepository;
    private final ApiVersionRepository versionRepository;
    private final AuthzService authzService;
    private final CurrentUser currentUser;
    private final AuditLogService auditLogService;
    private final InFlightRequestTracker inFlightRequestTracker;
    private final ThemeRepository themeRepository;
    private final ThemeService themeService;
    private final ApprovalService approvalService;

    public ApiManagementService(
            ApiDefinitionRepository definitionRepository,
            ApiVersionRepository versionRepository,
            AuthzService authzService,
            CurrentUser currentUser,
            AuditLogService auditLogService,
            InFlightRequestTracker inFlightRequestTracker,
            ThemeRepository themeRepository,
            ThemeService themeService,
            @Lazy ApprovalService approvalService) {
        this.definitionRepository = definitionRepository;
        this.versionRepository = versionRepository;
        this.authzService = authzService;
        this.currentUser = currentUser;
        this.auditLogService = auditLogService;
        this.inFlightRequestTracker = inFlightRequestTracker;
        this.themeRepository = themeRepository;
        this.themeService = themeService;
        this.approvalService = approvalService;
    }

    public List<ApiDefinition> listDefinitions() {
        authzService.requireApiRead();
        if (authzService.isSuperAdmin()) {
            return definitionRepository.findAll();
        }
        List<Long> themeIds = themeService.accessibleThemeIds();
        if (themeIds.isEmpty()) {
            return List.of();
        }
        return definitionRepository.findByThemeIdIn(themeIds);
    }

    public ApiDefinition getDefinition(Long id) {
        authzService.requireApiRead();
        ApiDefinition def = definitionRepository.findById(id).orElseThrow(() -> new BusinessException("API 不存在"));
        if (!authzService.isSuperAdmin()) {
            themeService.requireThemeRead(def.getThemeId());
        }
        return def;
    }

    public ApiDefinition getByCode(String apiCode) {
        return definitionRepository.findByApiCode(apiCode)
                .orElseThrow(() -> new BusinessException("API 不存在: " + apiCode));
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public ApiDefinition createDefinition(ApiDefinitionRequest req) {
        authzService.requireApiCreate();
        themeService.requireThemeWrite(req.getThemeId());
        if (definitionRepository.findByApiCode(req.getApiCode()).isPresent()) {
            throw new BusinessException("api_code 已存在");
        }
        Theme theme = themeRepository.findById(req.getThemeId())
                .orElseThrow(() -> new BusinessException("主题不存在"));
        req.setTheme(theme.getCode());
        if (!authzService.isSuperAdmin()) {
            approvalService.submit(ApprovalResourceType.API_DEFINITION, null, ApprovalAction.CREATE,
                    req.getThemeId(), "新建 API: " + req.getApiCode(), req);
            throw new BusinessException(202, "已提交审批，请在「待我审批」或「审批中心」查看进度");
        }
        return createDefinitionDirect(req, theme);
    }

    @Transactional
    public ApiDefinition createDefinitionDirect(ApiDefinitionRequest req, Theme theme) {
        ApiDefinition def = new ApiDefinition();
        def.setApiCode(req.getApiCode());
        def.setName(req.getName());
        def.setThemeId(theme.getId());
        def.setTheme(theme.getCode());
        def.setDescription(req.getDescription());
        String operator = currentUser.username();
        def.setCreatedBy(operator);
        def.setUpdatedBy(operator);
        ApiDefinition saved = definitionRepository.save(def);
        auditLogService.log("CREATE", "API_DEFINITION", String.valueOf(saved.getId()), saved.getApiCode(), saved);
        return saved;
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public ApiDefinition updateDefinition(Long id, ApiDefinitionRequest req) {
        ApiDefinition def = getDefinition(id);
        authzService.requireApiWrite(def);
        Long themeId = req.getThemeId() != null ? req.getThemeId() : def.getThemeId();
        themeService.requireThemeWrite(themeId);
        Theme theme = themeRepository.findById(themeId)
                .orElseThrow(() -> new BusinessException("主题不存在"));
        req.setTheme(theme.getCode());
        req.setThemeId(themeId);
        if (!authzService.isSuperAdmin()) {
            approvalService.submit(ApprovalResourceType.API_DEFINITION, id, ApprovalAction.UPDATE,
                    themeId, "更新 API: " + def.getApiCode(), req);
            throw new BusinessException(202, "已提交审批，请在「待我审批」或「审批中心」查看进度");
        }
        def.setName(req.getName());
        def.setThemeId(themeId);
        def.setTheme(theme.getCode());
        def.setDescription(req.getDescription());
        def.setUpdatedBy(currentUser.username());
        ApiDefinition saved = definitionRepository.save(def);
        auditLogService.log("UPDATE", "API_DEFINITION", String.valueOf(saved.getId()), saved.getApiCode(), saved);
        return saved;
    }

    @Transactional
    public void deleteDefinition(Long id) {
        ApiDefinition def = getDefinition(id);
        themeService.requireThemeAdmin(def.getThemeId());
        definitionRepository.deleteById(id);
        auditLogService.log("DELETE", "API_DEFINITION", String.valueOf(id), def.getApiCode(), null);
    }

    public List<ApiVersion> listVersions(Long apiId) {
        authzService.requireApiRead();
        return versionRepository.findByApiIdOrderByVersionNoDesc(apiId);
    }

    public ApiVersion getVersion(Long versionId) {
        authzService.requireApiRead();
        return versionRepository.findById(versionId)
                .orElseThrow(() -> new BusinessException("版本不存在"));
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public ApiVersion createVersion(Long apiId, ApiVersionRequest req) {
        ApiDefinition def = getDefinition(apiId);
        authzService.requireApiWrite(def);
        if (!authzService.isSuperAdmin()) {
            Map<String, Object> payload = Map.of("apiId", apiId, "version", req);
            approvalService.submit(ApprovalResourceType.API_VERSION, null, ApprovalAction.CREATE,
                    def.getThemeId(), "新建版本: " + def.getApiCode(), payload);
            throw new BusinessException(202, "已提交审批，请在「待我审批」或「审批中心」查看进度");
        }
        return createVersionDirect(apiId, req);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public ApiVersion updateVersion(Long versionId, ApiVersionRequest req) {
        ApiVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new BusinessException("版本不存在"));
        ApiDefinition def = getDefinition(version.getApiId());
        authzService.requireApiWrite(def);
        if (version.getStatus() == PublishStatus.PUBLISHED || version.getStatus() == PublishStatus.SUSPENDED) {
            throw new BusinessException("已发布或已暂停版本请新建版本号修改，勿直接编辑");
        }
        if (!authzService.isSuperAdmin()) {
            approvalService.submit(ApprovalResourceType.API_VERSION, versionId, ApprovalAction.UPDATE,
                    def.getThemeId(), "更新版本: " + def.getApiCode() + ":v" + version.getVersionNo(), req);
            throw new BusinessException(202, "已提交审批，请在「待我审批」或「审批中心」查看进度");
        }
        return updateVersionDirect(versionId, req);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public ApiVersion publish(Long versionId, String operator) {
        ApiVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new BusinessException("版本不存在"));
        ApiDefinition def = getDefinition(version.getApiId());
        authzService.requireApiWrite(def);
        if (version.getStatus() != PublishStatus.DRAFT) {
            throw new BusinessException("仅草稿版本可发布");
        }
        if (!authzService.isSuperAdmin()) {
            approvalService.submit(ApprovalResourceType.API_VERSION, versionId, ApprovalAction.PUBLISH,
                    def.getThemeId(), "发布版本: " + def.getApiCode() + ":v" + version.getVersionNo(), Map.of());
            throw new BusinessException(202, "已提交审批，请在「待我审批」或「审批中心」查看进度");
        }
        return publishDirect(versionId, operator);
    }

    @Transactional
    public ApiVersion publishDirect(Long versionId, String operator) {
        ApiVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new BusinessException("版本不存在"));
        ApiDefinition def = getDefinition(version.getApiId());
        if (inFlightRequestTracker.hasInFlight(def.getApiCode())) {
            throw new BusinessException("当前有请求正在处理该 API（" + def.getApiCode()
                    + "），无法发布，请稍后重试。进行中: " + inFlightRequestTracker.count(def.getApiCode()));
        }
        if (version.getStatus() != PublishStatus.DRAFT) {
            throw new BusinessException("仅草稿版本可发布");
        }
        String op = operator != null ? operator : currentUser.username();
        for (ApiVersion other : versionRepository.findByApiIdAndStatus(def.getId(), PublishStatus.PUBLISHED)) {
            if (!other.getId().equals(versionId)) {
                other.setStatus(PublishStatus.DEPRECATED);
                other.setUpdatedBy(op);
                ApiVersion deprecated = versionRepository.save(other);
                auditLogService.log("DEPRECATE", "API_VERSION", String.valueOf(deprecated.getId()),
                        def.getApiCode() + ":v" + deprecated.getVersionNo(), deprecated);
            }
        }
        version.setStatus(PublishStatus.PUBLISHED);
        version.setPublishedAt(LocalDateTime.now());
        version.setUpdatedBy(op);
        ApiVersion saved = versionRepository.save(version);
        auditLogService.log("PUBLISH", "API_VERSION", String.valueOf(saved.getId()),
                def.getApiCode() + ":v" + saved.getVersionNo(), saved);
        return saved;
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public ApiVersion suspend(Long versionId) {
        ApiVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new BusinessException("版本不存在"));
        ApiDefinition def = getDefinition(version.getApiId());
        authzService.requireApiWrite(def);
        if (version.getStatus() != PublishStatus.PUBLISHED) {
            throw new BusinessException("仅已发布版本可关闭");
        }
        if (!authzService.isSuperAdmin()) {
            approvalService.submit(ApprovalResourceType.API_VERSION, versionId, ApprovalAction.SUSPEND,
                    def.getThemeId(), "关闭版本: " + def.getApiCode() + ":v" + version.getVersionNo(), Map.of());
            throw new BusinessException(202, "已提交审批，请在「审批中心」查看进度");
        }
        return suspendDirect(versionId);
    }

    @Transactional
    public ApiVersion suspendDirect(Long versionId) {
        ApiVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new BusinessException("版本不存在"));
        ApiDefinition def = getDefinition(version.getApiId());
        if (version.getStatus() != PublishStatus.PUBLISHED) {
            throw new BusinessException("仅已发布版本可关闭");
        }
        version.setStatus(PublishStatus.SUSPENDED);
        version.setUpdatedBy(currentUser.username());
        ApiVersion saved = versionRepository.save(version);
        auditLogService.log("SUSPEND", "API_VERSION", String.valueOf(saved.getId()),
                def.getApiCode() + ":v" + saved.getVersionNo(), null);
        return saved;
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public ApiVersion resume(Long versionId) {
        ApiVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new BusinessException("版本不存在"));
        ApiDefinition def = getDefinition(version.getApiId());
        authzService.requireApiWrite(def);
        if (version.getStatus() != PublishStatus.SUSPENDED) {
            throw new BusinessException("仅已暂停版本可重启");
        }
        if (inFlightRequestTracker.hasInFlight(def.getApiCode())) {
            throw new BusinessException("当前有请求正在处理该 API（" + def.getApiCode()
                    + "），无法重启，请稍后重试。进行中: " + inFlightRequestTracker.count(def.getApiCode()));
        }
        if (!authzService.isSuperAdmin()) {
            approvalService.submit(ApprovalResourceType.API_VERSION, versionId, ApprovalAction.RESUME,
                    def.getThemeId(), "重启版本: " + def.getApiCode() + ":v" + version.getVersionNo(), Map.of());
            throw new BusinessException(202, "已提交审批，请在「审批中心」查看进度");
        }
        return resumeDirect(versionId);
    }

    @Transactional
    public ApiVersion resumeDirect(Long versionId) {
        ApiVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new BusinessException("版本不存在"));
        ApiDefinition def = getDefinition(version.getApiId());
        if (version.getStatus() != PublishStatus.SUSPENDED) {
            throw new BusinessException("仅已暂停版本可重启");
        }
        if (inFlightRequestTracker.hasInFlight(def.getApiCode())) {
            throw new BusinessException("当前有请求正在处理该 API（" + def.getApiCode()
                    + "），无法重启，请稍后重试。进行中: " + inFlightRequestTracker.count(def.getApiCode()));
        }
        String op = currentUser.username();
        for (ApiVersion other : versionRepository.findByApiIdAndStatus(def.getId(), PublishStatus.PUBLISHED)) {
            other.setStatus(PublishStatus.DEPRECATED);
            other.setUpdatedBy(op);
            ApiVersion deprecated = versionRepository.save(other);
            auditLogService.log("DEPRECATE", "API_VERSION", String.valueOf(deprecated.getId()),
                    def.getApiCode() + ":v" + deprecated.getVersionNo(), null);
        }
        version.setStatus(PublishStatus.PUBLISHED);
        version.setPublishedAt(LocalDateTime.now());
        version.setUpdatedBy(op);
        ApiVersion saved = versionRepository.save(version);
        auditLogService.log("RESUME", "API_VERSION", String.valueOf(saved.getId()),
                def.getApiCode() + ":v" + saved.getVersionNo(), null);
        return saved;
    }

    @Transactional
    public ApiVersion deprecate(Long versionId, String operator) {
        ApiVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new BusinessException("版本不存在"));
        ApiDefinition def = getDefinition(version.getApiId());
        authzService.requireApiWrite(def);
        version.setStatus(PublishStatus.DEPRECATED);
        version.setUpdatedBy(operator != null ? operator : currentUser.username());
        ApiVersion saved = versionRepository.save(version);
        auditLogService.log("DEPRECATE", "API_VERSION", String.valueOf(saved.getId()),
                def.getApiCode() + ":v" + saved.getVersionNo(), saved);
        return saved;
    }

    public ApiVersion resolvePublishedVersion(ApiDefinition def, Integer versionNo) {
        if (versionNo != null) {
            ApiVersion version = versionRepository.findByApiIdAndVersionNo(def.getId(), versionNo)
                    .orElseThrow(() -> new BusinessException("指定版本未发布或不存在"));
            if (version.getStatus() == PublishStatus.SUSPENDED) {
                throw new BusinessException("该 API 版本已暂停，暂不接受新请求");
            }
            if (version.getStatus() != PublishStatus.PUBLISHED) {
                throw new BusinessException("指定版本未发布或不存在");
            }
            return version;
        }
        return versionRepository.findFirstByApiIdAndStatusOrderByVersionNoDesc(def.getId(), PublishStatus.PUBLISHED)
                .orElseThrow(() -> new BusinessException("API 暂无可用版本，可能尚未发布或已全部暂停"));
    }

    public Map<String, Object> buildApiPath(ApiDefinition def, ApiVersion version) {
        Map<String, Object> info = new HashMap<>();
        String theme = def.getTheme() != null ? def.getTheme() : "default";
        info.put("path", "/api/data/v" + version.getVersionNo() + "/" + theme + "/" + def.getApiCode());
        info.put("method", "GET/POST");
        info.put("version", version.getVersionNo());
        return info;
    }

    private void applyVersion(ApiVersion version, ApiVersionRequest req) {
        version.setDatasourceId(req.getDatasourceId());
        version.setSqlTemplate(req.getSqlTemplate());
        version.setResponseMode(req.getResponseMode());
        version.setResponseConfig(req.getResponseConfig() != null ? req.getResponseConfig() : defaultResponseConfig(req));
        String operator = currentUser.username();
        if (version.getCreatedBy() == null) {
            version.setCreatedBy(operator);
        }
        version.setUpdatedBy(operator);
    }

    private Map<String, Object> defaultResponseConfig(ApiVersionRequest req) {
        Map<String, Object> config = new HashMap<>();
        config.put("timeoutSec", 60);
        config.put("maxPageSize", 500);
        config.put("maxOffset", 100000);
        return config;
    }

    @Transactional
    public ApiVersion createVersionDirect(Long apiId, ApiVersionRequest req) {
        ApiDefinition def = getDefinition(apiId);
        SqlSecurityValidator.validateReadOnlySql(req.getSqlTemplate());
        int nextVersion = versionRepository.findByApiIdOrderByVersionNoDesc(apiId).stream()
                .mapToInt(ApiVersion::getVersionNo).max().orElse(0) + 1;
        ApiVersion version = new ApiVersion();
        version.setApiId(apiId);
        version.setVersionNo(nextVersion);
        applyVersion(version, req);
        ApiVersion saved = versionRepository.save(version);
        auditLogService.log("CREATE", "API_VERSION", String.valueOf(saved.getId()),
                def.getApiCode() + ":v" + saved.getVersionNo(), saved);
        return saved;
    }

    @Transactional
    public ApiVersion updateVersionDirect(Long versionId, ApiVersionRequest req) {
        ApiVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new BusinessException("版本不存在"));
        ApiDefinition def = getDefinition(version.getApiId());
        if (version.getStatus() == PublishStatus.PUBLISHED || version.getStatus() == PublishStatus.SUSPENDED) {
            throw new BusinessException("已发布或已暂停版本请新建版本号修改，勿直接编辑");
        }
        SqlSecurityValidator.validateReadOnlySql(req.getSqlTemplate());
        applyVersion(version, req);
        ApiVersion saved = versionRepository.save(version);
        auditLogService.log("UPDATE", "API_VERSION", String.valueOf(saved.getId()),
                def.getApiCode() + ":v" + saved.getVersionNo(), saved);
        return saved;
    }

    public Map<String, Object> buildApiDoc(Long versionId) {
        ApiVersion version = getVersion(versionId);
        ApiDefinition def = getDefinition(version.getApiId());
        Map<String, Object> doc = buildApiPath(def, version);
        doc.put("apiCode", def.getApiCode());
        doc.put("apiName", def.getName());
        doc.put("sqlTemplate", version.getSqlTemplate());
        doc.put("responseConfig", version.getResponseConfig());
        doc.put("status", version.getStatus());
        doc.put("authHint", "请求头: X-Api-Key: <key> 或 Authorization: Bearer <key>");
        doc.put("pageParams", "page, pageSize (query 必填)");
        return doc;
    }
}
