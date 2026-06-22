package com.apigateway.service;

import com.apigateway.dto.DatasourceRequest;
import com.apigateway.entity.Datasource;
import com.apigateway.exception.BusinessException;
import com.apigateway.repository.DatasourceRepository;
import com.apigateway.security.AuthzService;
import lombok.RequiredArgsConstructor;
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

    public List<Datasource> list() {
        authzService.requireAuthenticated();
        return repository.findAll();
    }

    public Datasource get(Long id) {
        authzService.requireAuthenticated();
        return repository.findById(id).orElseThrow(() -> new BusinessException("数据源不存在"));
    }

    @Transactional
    public Datasource create(DatasourceRequest req) {
        authzService.requireSuperAdmin();
        Datasource ds = toEntity(new Datasource(), req);
        Datasource saved = repository.save(ds);
        auditLogService.log("CREATE", "DATASOURCE", String.valueOf(saved.getId()), saved.getName(), saved);
        return saved;
    }

    @Transactional
    public Datasource update(Long id, DatasourceRequest req) {
        authzService.requireSuperAdmin();
        Datasource ds = get(id);
        toEntity(ds, req);
        connectionPoolManager.evict(id);
        Datasource saved = repository.save(ds);
        auditLogService.log("UPDATE", "DATASOURCE", String.valueOf(saved.getId()), saved.getName(), saved);
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        authzService.requireSuperAdmin();
        Datasource ds = get(id);
        connectionPoolManager.evict(id);
        repository.deleteById(id);
        auditLogService.log("DELETE", "DATASOURCE", String.valueOf(id), ds.getName(), null);
    }

    public boolean test(Long id) {
        authzService.requireSuperAdmin();
        return connectionPoolManager.testConnection(get(id));
    }

    public boolean test(DatasourceRequest req, Long existingId) {
        authzService.requireSuperAdmin();
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
        Map<String, Object> template = new HashMap<>();
        template.put("pool.minIdle", 2);
        template.put("pool.maxActive", 10);
        template.put("connectTimeoutMs", 5000);
        if ("DORIS".equalsIgnoreCase(type)) {
            template.put("protocol", "mysql");
            template.put("queryTimeoutSec", 300);
        } else if ("CLICKHOUSE".equalsIgnoreCase(type)) {
            template.put("protocol", "http");
            template.put("compress", true);
            template.put("maxThreads", 4);
        }
        return template;
    }

    private Datasource toEntity(Datasource ds, DatasourceRequest req) {
        ds.setName(trim(req.getName()));
        ds.setType(req.getType());
        ds.setHost(trim(req.getHost()));
        ds.setPort(req.getPort());
        ds.setDatabaseName(trim(req.getDatabaseName()));
        ds.setUsername(trim(req.getUsername()));
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            ds.setPassword(req.getPassword());
        }
        ds.setDefaultParams(req.getDefaultParams() != null ? req.getDefaultParams() : Map.of());
        ds.setEnv(trim(req.getEnv()));
        ds.setReadonly(req.getReadonly());
        ds.setStatus(req.getStatus());
        ds.setDescription(trim(req.getDescription()));
        return ds;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
