package com.apigateway.service;

import com.apigateway.dto.DatasourceRequest;
import com.apigateway.entity.Datasource;
import com.apigateway.exception.BusinessException;
import com.apigateway.repository.DatasourceRepository;
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

    public List<Datasource> list() {
        return repository.findAll();
    }

    public Datasource get(Long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException("数据源不存在"));
    }

    @Transactional
    public Datasource create(DatasourceRequest req) {
        Datasource ds = toEntity(new Datasource(), req);
        return repository.save(ds);
    }

    @Transactional
    public Datasource update(Long id, DatasourceRequest req) {
        Datasource ds = get(id);
        toEntity(ds, req);
        connectionPoolManager.evict(id);
        return repository.save(ds);
    }

    @Transactional
    public void delete(Long id) {
        connectionPoolManager.evict(id);
        repository.deleteById(id);
    }

    public boolean test(Long id) {
        return connectionPoolManager.testConnection(get(id));
    }

    public Map<String, Object> defaultParamTemplate(String type) {
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
        ds.setName(req.getName());
        ds.setType(req.getType());
        ds.setHost(req.getHost());
        ds.setPort(req.getPort());
        ds.setDatabaseName(req.getDatabaseName());
        ds.setUsername(req.getUsername());
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            ds.setPassword(req.getPassword());
        }
        ds.setDefaultParams(req.getDefaultParams() != null ? req.getDefaultParams() : Map.of());
        ds.setEnv(req.getEnv());
        ds.setReadonly(req.getReadonly());
        ds.setStatus(req.getStatus());
        ds.setDescription(req.getDescription());
        return ds;
    }
}
