package com.apigateway.service;

import com.apigateway.datasource.DatasourceDriverRegistry;
import com.apigateway.dto.DatasourceRequest;
import com.apigateway.dto.DatasourceResponse;
import com.apigateway.entity.ApprovalAction;
import com.apigateway.entity.ApprovalResourceType;
import com.apigateway.entity.Datasource;
import com.apigateway.exception.BusinessException;
import com.apigateway.repository.DatasourceRepository;
import com.apigateway.security.AuthzService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DatasourceService {

    private final DatasourceRepository repository;
    private final ConnectionPoolManager connectionPoolManager;
    private final AuthzService authzService;
    private final AuditLogService auditLogService;
    private final ThemeService themeService;
    private final @Lazy ApprovalService approvalService;
    private final DatasourceDriverRegistry driverRegistry;
    private final DatasourcePasswordResolver passwordResolver;

    public List<DatasourceResponse> list() {
        authzService.requireAuthenticated();
        if (authzService.isSuperAdmin()) {
            return repository.findAll().stream().map(DatasourceResponse::from).toList();
        }
        List<Long> themeIds = themeService.accessibleThemeIds();
        if (themeIds.isEmpty()) {
            return List.of();
        }
        return repository.findByThemeIdIn(themeIds).stream().map(DatasourceResponse::from).toList();
    }

    public DatasourceResponse getResponse(Long id) {
        return DatasourceResponse.from(get(id));
    }

    public Datasource get(Long id) {
        authzService.requireAuthenticated();
        Datasource ds = repository.findById(id).orElseThrow(() -> new BusinessException("数据源不存在"));
        if (!authzService.isSuperAdmin()) {
            themeService.requireThemeRead(ds.getThemeId());
        }
        return ds;
    }

    @Transactional
    public Datasource createDirect(DatasourceRequest req) {
        Datasource ds = toEntity(new Datasource(), req);
        Datasource saved = repository.save(ds);
        auditLogService.log("CREATE", "DATASOURCE", String.valueOf(saved.getId()), saved.getName(), saved);
        return saved;
    }

    @Transactional
    public Datasource updateDirect(Long id, DatasourceRequest req) {
        Datasource ds = get(id);
        toEntity(ds, req);
        connectionPoolManager.evict(id);
        Datasource saved = repository.save(ds);
        auditLogService.log("UPDATE", "DATASOURCE", String.valueOf(saved.getId()), saved.getName(), saved);
        return saved;
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public DatasourceResponse create(DatasourceRequest req) {
        if (req.getThemeId() == null) {
            throw new BusinessException("请选择主题");
        }
        themeService.requireThemeWrite(req.getThemeId());
        if (!authzService.isSuperAdmin()) {
            approvalService.submit(ApprovalResourceType.DATASOURCE, null, ApprovalAction.CREATE,
                    req.getThemeId(), "新建数据源: " + req.getName(), req);
            throw new BusinessException(202, "已提交审批，请在「待我审批」或「审批中心」查看进度");
        }
        return DatasourceResponse.from(createDirect(req));
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public DatasourceResponse update(Long id, DatasourceRequest req) {
        Datasource ds = get(id);
        Long themeId = req.getThemeId() != null ? req.getThemeId() : ds.getThemeId();
        themeService.requireThemeWrite(themeId);
        req.setThemeId(themeId);
        if (!authzService.isSuperAdmin()) {
            approvalService.submit(ApprovalResourceType.DATASOURCE, id, ApprovalAction.UPDATE,
                    themeId, "更新数据源: " + ds.getName(), req);
            throw new BusinessException(202, "已提交审批，请在「待我审批」或「审批中心」查看进度");
        }
        return DatasourceResponse.from(updateDirect(id, req));
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void delete(Long id) {
        Datasource ds = get(id);
        themeService.requireThemeAdmin(ds.getThemeId());
        if (!authzService.isSuperAdmin()) {
            approvalService.submit(ApprovalResourceType.DATASOURCE, id, ApprovalAction.DELETE,
                    ds.getThemeId(), "删除数据源: " + ds.getName(), Map.of("name", ds.getName()));
            throw new BusinessException(202, "已提交审批，请在「待我审批」或「审批中心」查看进度");
        }
        deleteDirect(id);
    }

    @Transactional
    public void deleteDirect(Long id) {
        Datasource ds = get(id);
        connectionPoolManager.evict(id);
        repository.deleteById(id);
        auditLogService.log("DELETE", "DATASOURCE", String.valueOf(id), ds.getName(), null);
    }

    public boolean test(Long id) {
        Datasource ds = get(id);
        themeService.requireThemeWrite(ds.getThemeId());
        return connectionPoolManager.testConnection(ds);
    }

    public boolean test(DatasourceRequest req, Long existingId) {
        if (req.getThemeId() != null) {
            themeService.requireThemeWrite(req.getThemeId());
        } else if (existingId != null) {
            themeService.requireThemeWrite(get(existingId).getThemeId());
        } else {
            authzService.requireSuperAdmin();
        }
        validateConnectionFields(req);
        Datasource ds = toEntity(new Datasource(), req);
        if (existingId != null) {
            ds.setId(existingId);
            if (req.getPassword() == null || req.getPassword().isBlank()) {
                ds.setPassword(get(existingId).getPassword());
            }
        }
        return connectionPoolManager.testConnection(ds);
    }

    private void validateConnectionFields(DatasourceRequest req) {
        if (req.getType() == null) {
            throw new BusinessException("请选择数据源类型");
        }
        if (req.getHost() == null || req.getHost().isBlank()) {
            throw new BusinessException("Host 不能为空");
        }
        if (req.getPort() == null || req.getPort() <= 0) {
            throw new BusinessException("端口无效");
        }
        if (req.getDatabaseName() == null || req.getDatabaseName().isBlank()) {
            throw new BusinessException("数据库不能为空");
        }
    }

    public Map<String, Object> defaultParamTemplate(String type) {
        authzService.requireAuthenticated();
        return new HashMap<>(driverRegistry.require(type).defaultParams());
    }

    private Datasource toEntity(Datasource ds, DatasourceRequest req) {
        ds.setName(trim(req.getName()));
        ds.setType(req.getType());
        ds.setHost(trim(req.getHost()));
        ds.setPort(req.getPort());
        ds.setDatabaseName(trim(req.getDatabaseName()));
        ds.setUsername(trim(req.getUsername()));
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            ds.setPassword(passwordResolver.encryptForStorage(req.getPassword()));
        }
        ds.setDefaultParams(req.getDefaultParams() != null ? req.getDefaultParams() : Map.of());
        ds.setEnv(trim(req.getEnv()));
        ds.setReadonly(req.getReadonly());
        ds.setStatus(req.getStatus());
        ds.setDescription(trim(req.getDescription()));
        if (req.getThemeId() != null) {
            ds.setThemeId(req.getThemeId());
        }
        return ds;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
