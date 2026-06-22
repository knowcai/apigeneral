package com.apigateway.service;

import com.apigateway.dto.ApiDefinitionRequest;
import com.apigateway.dto.ApiVersionRequest;
import com.apigateway.entity.ApiDefinition;
import com.apigateway.entity.ApiVersion;
import com.apigateway.entity.PublishStatus;
import com.apigateway.exception.BusinessException;
import com.apigateway.repository.ApiDefinitionRepository;
import com.apigateway.repository.ApiVersionRepository;
import com.apigateway.security.AuthzService;
import com.apigateway.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ApiManagementService {

    private final ApiDefinitionRepository definitionRepository;
    private final ApiVersionRepository versionRepository;
    private final AuthzService authzService;
    private final CurrentUser currentUser;
    private final AuditLogService auditLogService;
    private final InFlightRequestTracker inFlightRequestTracker;

    public List<ApiDefinition> listDefinitions() {
        authzService.requireApiRead();
        return definitionRepository.findAll();
    }

    public ApiDefinition getDefinition(Long id) {
        authzService.requireApiRead();
        return definitionRepository.findById(id).orElseThrow(() -> new BusinessException("API 不存在"));
    }

    public ApiDefinition getByCode(String apiCode) {
        return definitionRepository.findByApiCode(apiCode)
                .orElseThrow(() -> new BusinessException("API 不存在: " + apiCode));
    }

    @Transactional
    public ApiDefinition createDefinition(ApiDefinitionRequest req) {
        authzService.requireApiCreate();
        if (definitionRepository.findByApiCode(req.getApiCode()).isPresent()) {
            throw new BusinessException("api_code 已存在");
        }
        ApiDefinition def = new ApiDefinition();
        def.setApiCode(req.getApiCode());
        def.setName(req.getName());
        def.setTheme(req.getTheme());
        def.setDescription(req.getDescription());
        String operator = currentUser.username();
        def.setCreatedBy(operator);
        def.setUpdatedBy(operator);
        ApiDefinition saved = definitionRepository.save(def);
        auditLogService.log("CREATE", "API_DEFINITION", String.valueOf(saved.getId()), saved.getApiCode(), saved);
        return saved;
    }

    @Transactional
    public ApiDefinition updateDefinition(Long id, ApiDefinitionRequest req) {
        ApiDefinition def = getDefinition(id);
        authzService.requireApiWrite(def);
        def.setName(req.getName());
        def.setTheme(req.getTheme());
        def.setDescription(req.getDescription());
        def.setUpdatedBy(currentUser.username());
        ApiDefinition saved = definitionRepository.save(def);
        auditLogService.log("UPDATE", "API_DEFINITION", String.valueOf(saved.getId()), saved.getApiCode(), saved);
        return saved;
    }

    @Transactional
    public void deleteDefinition(Long id) {
        ApiDefinition def = getDefinition(id);
        authzService.requireApiWrite(def);
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

    @Transactional
    public ApiVersion createVersion(Long apiId, ApiVersionRequest req) {
        ApiDefinition def = getDefinition(apiId);
        authzService.requireApiWrite(def);
        int nextVersion = versionRepository.findByApiIdOrderByVersionNoDesc(apiId).stream()
                .mapToInt(ApiVersion::getVersionNo)
                .max()
                .orElse(0) + 1;
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
    public ApiVersion updateVersion(Long versionId, ApiVersionRequest req) {
        ApiVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new BusinessException("版本不存在"));
        ApiDefinition def = getDefinition(version.getApiId());
        authzService.requireApiWrite(def);
        if (version.getStatus() == PublishStatus.PUBLISHED) {
            throw new BusinessException("已发布版本请新建版本号修改，勿直接编辑");
        }
        applyVersion(version, req);
        ApiVersion saved = versionRepository.save(version);
        auditLogService.log("UPDATE", "API_VERSION", String.valueOf(saved.getId()),
                def.getApiCode() + ":v" + saved.getVersionNo(), saved);
        return saved;
    }

    @Transactional
    public ApiVersion publish(Long versionId, String operator) {
        ApiVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new BusinessException("版本不存在"));
        ApiDefinition def = getDefinition(version.getApiId());
        authzService.requireApiWrite(def);
        if (inFlightRequestTracker.hasInFlight(def.getApiCode())) {
            throw new BusinessException("当前有请求正在处理该 API（" + def.getApiCode()
                    + "），无法发布，请稍后重试。进行中: " + inFlightRequestTracker.count(def.getApiCode()));
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
            return versionRepository.findByApiIdAndVersionNo(def.getId(), versionNo)
                    .filter(v -> v.getStatus() == PublishStatus.PUBLISHED)
                    .orElseThrow(() -> new BusinessException("指定版本未发布或不存在"));
        }
        return versionRepository.findFirstByApiIdAndStatusOrderByVersionNoDesc(def.getId(), PublishStatus.PUBLISHED)
                .orElseThrow(() -> new BusinessException("API 尚无已发布版本"));
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
        config.put("ipWhitelist", List.of());
        config.put("maxPageSize", 500);
        config.put("maxOffset", 100000);
        return config;
    }
}
