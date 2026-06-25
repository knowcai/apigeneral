package com.apigateway.service;

import com.apigateway.dto.ApiDefinitionRequest;
import com.apigateway.dto.ApiVersionRequest;
import com.apigateway.dto.DatasourceRequest;
import com.apigateway.entity.*;
import com.apigateway.exception.BusinessException;
import com.apigateway.repository.ApiDefinitionRepository;
import com.apigateway.repository.ApiVersionRepository;
import com.apigateway.repository.DatasourceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ApprovalDiffService {

    private final ApiDefinitionRepository definitionRepository;
    private final ApiVersionRepository versionRepository;
    private final DatasourceRepository datasourceRepository;
    private final ObjectMapper objectMapper;

    public List<Map<String, Object>> buildDiff(ApprovalResourceType type, ApprovalAction action,
                                               Long resourceId, String payloadJson) {
        if (action == ApprovalAction.CREATE || payloadJson == null || payloadJson.isBlank()) {
            return List.of();
        }
        try {
            Object payload = objectMapper.readValue(payloadJson, Object.class);
            Map<String, Object> before = loadBefore(type, action, resourceId, payload);
            Map<String, Object> after = loadAfter(type, action, payload);
            return diffMaps(before, after);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, Object> loadBefore(ApprovalResourceType type, ApprovalAction action,
                                           Long resourceId, Object payload) {
        return switch (type) {
            case API_DEFINITION -> {
                if (resourceId == null) yield Map.of();
                ApiDefinition def = definitionRepository.findById(resourceId)
                        .orElseThrow(() -> new BusinessException("API 不存在"));
                yield Map.of(
                        "name", def.getName(),
                        "description", nullToEmpty(def.getDescription())
                );
            }
            case API_VERSION -> {
                if (resourceId == null) yield Map.of();
                ApiVersion ver = versionRepository.findById(resourceId)
                        .orElseThrow(() -> new BusinessException("版本不存在"));
                yield versionFields(ver);
            }
            case DATASOURCE -> {
                if (resourceId == null) yield Map.of();
                Datasource ds = datasourceRepository.findById(resourceId)
                        .orElseThrow(() -> new BusinessException("数据源不存在"));
                yield datasourceFields(ds);
            }
        };
    }

    private Map<String, Object> loadAfter(ApprovalResourceType type, ApprovalAction action, Object payload) {
        return switch (type) {
            case API_DEFINITION -> {
                ApiDefinitionRequest req = objectMapper.convertValue(payload, ApiDefinitionRequest.class);
                yield Map.of(
                        "name", req.getName(),
                        "description", nullToEmpty(req.getDescription())
                );
            }
            case API_VERSION -> {
                if (action == ApprovalAction.CREATE) {
                    Map<?, ?> map = objectMapper.convertValue(payload, Map.class);
                    ApiVersionRequest req = objectMapper.convertValue(map.get("version"), ApiVersionRequest.class);
                    yield versionRequestFields(req);
                }
                ApiVersionRequest req = objectMapper.convertValue(payload, ApiVersionRequest.class);
                yield versionRequestFields(req);
            }
            case DATASOURCE -> {
                DatasourceRequest req = objectMapper.convertValue(payload, DatasourceRequest.class);
                yield datasourceRequestFields(req);
            }
        };
    }

    private Map<String, Object> versionFields(ApiVersion ver) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("datasourceId", ver.getDatasourceId());
        m.put("sqlTemplate", ver.getSqlTemplate());
        m.put("responseConfig", ver.getResponseConfig());
        return m;
    }

    private Map<String, Object> versionRequestFields(ApiVersionRequest req) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("datasourceId", req.getDatasourceId());
        m.put("sqlTemplate", req.getSqlTemplate());
        m.put("responseConfig", req.getResponseConfig());
        return m;
    }

    private Map<String, Object> datasourceFields(Datasource ds) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", ds.getName());
        m.put("type", ds.getType() != null ? ds.getType().name() : "");
        m.put("host", ds.getHost());
        m.put("port", ds.getPort());
        m.put("database", ds.getDatabaseName());
        m.put("readonly", ds.getReadonly());
        return m;
    }

    private Map<String, Object> datasourceRequestFields(DatasourceRequest req) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", req.getName());
        m.put("type", req.getType());
        m.put("host", req.getHost());
        m.put("port", req.getPort());
        m.put("database", req.getDatabaseName());
        m.put("readonly", req.getReadonly());
        return m;
    }

    private List<Map<String, Object>> diffMaps(Map<String, Object> before, Map<String, Object> after) {
        Set<String> keys = new TreeSet<>();
        keys.addAll(before.keySet());
        keys.addAll(after.keySet());
        List<Map<String, Object>> diffs = new ArrayList<>();
        for (String key : keys) {
            Object oldVal = before.get(key);
            Object newVal = after.get(key);
            if (Objects.equals(stringify(oldVal), stringify(newVal))) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("field", key);
            row.put("before", oldVal);
            row.put("after", newVal);
            diffs.add(row);
        }
        return diffs;
    }

    private String stringify(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Map || value instanceof List) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (Exception e) {
                return value.toString();
            }
        }
        return String.valueOf(value);
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
