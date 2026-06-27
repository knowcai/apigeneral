package com.apigateway.service;

import com.apigateway.dto.ApiDefinitionRequest;
import com.apigateway.dto.ApiVersionRequest;
import com.apigateway.dto.DatasourceRequest;
import com.apigateway.entity.*;
import com.apigateway.exception.BusinessException;
import com.apigateway.repository.*;
import com.apigateway.security.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Service
public class ApprovalApplyService {

    private final ApiDefinitionRepository definitionRepository;
    private final ApiVersionRepository versionRepository;
    private final DatasourceRepository datasourceRepository;
    private final ThemeRepository themeRepository;
    private final ApiManagementService apiManagementService;
    private final DatasourceService datasourceService;
    private final CurrentUser currentUser;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;
    private final ThemeApiKeyService themeApiKeyService;

    public ApprovalApplyService(
            ApiDefinitionRepository definitionRepository,
            ApiVersionRepository versionRepository,
            DatasourceRepository datasourceRepository,
            ThemeRepository themeRepository,
            @Lazy ApiManagementService apiManagementService,
            @Lazy DatasourceService datasourceService,
            CurrentUser currentUser,
            ObjectMapper objectMapper,
            AuditLogService auditLogService,
            @Lazy ThemeApiKeyService themeApiKeyService) {
        this.definitionRepository = definitionRepository;
        this.versionRepository = versionRepository;
        this.datasourceRepository = datasourceRepository;
        this.themeRepository = themeRepository;
        this.apiManagementService = apiManagementService;
        this.datasourceService = datasourceService;
        this.currentUser = currentUser;
        this.objectMapper = objectMapper;
        this.auditLogService = auditLogService;
        this.themeApiKeyService = themeApiKeyService;
    }

    @Transactional
    public Optional<String> apply(ApprovalResourceType type, Long resourceId, ApprovalAction action, Long themeId,
                                  Object payload) {
        return apply(type, resourceId, action, themeId, payload, null, null);
    }

    @Transactional
    public Optional<String> apply(ApprovalResourceType type, Long resourceId, ApprovalAction action, Long themeId,
                                  Object payload, Long submitterId, Long approvalRequestId) {
        return switch (type) {
            case API_DEFINITION -> {
                applyApiDefinition(action, themeId, payload, resourceId);
                yield Optional.empty();
            }
            case API_VERSION -> {
                applyApiVersion(action, payload, resourceId);
                yield Optional.empty();
            }
            case DATASOURCE -> {
                applyDatasource(action, themeId, payload, resourceId);
                yield Optional.empty();
            }
            case THEME_API_KEY -> themeApiKeyService.apply(action, themeId, resourceId, payload, submitterId, approvalRequestId);
            default -> throw new BusinessException("不支持的审批资源: " + type);
        };
    }

    private void applyApiDefinition(ApprovalAction action, Long themeId, Object payload, Long resourceId) {
        ApiDefinitionRequest req = convert(payload, ApiDefinitionRequest.class);
        Theme theme = themeRepository.findById(themeId).orElseThrow(() -> new BusinessException("主题不存在"));
        if (action == ApprovalAction.CREATE) {
            ApiDefinition def = apiManagementService.createDefinitionDirect(req, theme);
        } else if (action == ApprovalAction.UPDATE) {
            ApiDefinition def = definitionRepository.findById(resourceId)
                    .orElseThrow(() -> new BusinessException("API 不存在"));
            def.setName(req.getName());
            def.setDescription(req.getDescription());
            def.setUpdatedBy(currentUser.username());
            definitionRepository.save(def);
            auditLogService.log("UPDATE", "API_DEFINITION", String.valueOf(def.getId()), def.getApiCode(), def);
        } else if (action == ApprovalAction.DELETE) {
            apiManagementService.deleteDefinitionDirect(resourceId);
        } else {
            throw new BusinessException("不支持的 API 定义操作: " + action);
        }
    }

    private void applyApiVersion(ApprovalAction action, Object payload, Long resourceId) {
        if (action == ApprovalAction.CREATE) {
            Map<?, ?> map = convert(payload, Map.class);
            Long apiId = Long.valueOf(String.valueOf(map.get("apiId")));
            ApiVersionRequest req = convert(map.get("version"), ApiVersionRequest.class);
            apiManagementService.createVersionDirect(apiId, req);
        } else if (action == ApprovalAction.UPDATE) {
            ApiVersionRequest req = convert(payload, ApiVersionRequest.class);
            apiManagementService.updateVersionDirect(resourceId, req);
        } else if (action == ApprovalAction.PUBLISH) {
            apiManagementService.publishDirect(resourceId, currentUser.username());
        } else if (action == ApprovalAction.SUSPEND) {
            apiManagementService.suspendDirect(resourceId);
        } else if (action == ApprovalAction.RESUME) {
            apiManagementService.resumeDirect(resourceId);
        } else {
            throw new BusinessException("不支持的 API 版本操作: " + action);
        }
    }

    private void applyDatasource(ApprovalAction action, Long themeId, Object payload, Long resourceId) {
        if (action == ApprovalAction.DELETE) {
            datasourceService.deleteDirect(resourceId);
            return;
        }
        DatasourceRequest req = convert(payload, DatasourceRequest.class);
        req.setThemeId(themeId);
        if (action == ApprovalAction.CREATE) {
            datasourceService.createDirect(req);
        } else if (action == ApprovalAction.UPDATE) {
            datasourceService.updateDirect(resourceId, req);
        } else {
            throw new BusinessException("不支持的数据源操作: " + action);
        }
    }

    private <T> T convert(Object payload, Class<T> type) {
        return objectMapper.convertValue(payload, type);
    }
}
