package com.apigateway.service;

import com.apigateway.dto.ApiDefinitionRequest;
import com.apigateway.dto.ApiVersionRequest;
import com.apigateway.entity.ApiDefinition;
import com.apigateway.entity.ApiVersion;
import com.apigateway.entity.PublishStatus;
import com.apigateway.exception.BusinessException;
import com.apigateway.repository.ApiDefinitionRepository;
import com.apigateway.repository.ApiVersionRepository;
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

    public List<ApiDefinition> listDefinitions() {
        return definitionRepository.findAll();
    }

    public ApiDefinition getDefinition(Long id) {
        return definitionRepository.findById(id).orElseThrow(() -> new BusinessException("API 不存在"));
    }

    public ApiDefinition getByCode(String apiCode) {
        return definitionRepository.findByApiCode(apiCode)
                .orElseThrow(() -> new BusinessException("API 不存在: " + apiCode));
    }

    @Transactional
    public ApiDefinition createDefinition(ApiDefinitionRequest req) {
        if (definitionRepository.findByApiCode(req.getApiCode()).isPresent()) {
            throw new BusinessException("api_code 已存在");
        }
        ApiDefinition def = new ApiDefinition();
        def.setApiCode(req.getApiCode());
        def.setName(req.getName());
        def.setTheme(req.getTheme());
        def.setDescription(req.getDescription());
        def.setCreatedBy(req.getCreatedBy());
        def.setUpdatedBy(req.getUpdatedBy());
        return definitionRepository.save(def);
    }

    @Transactional
    public ApiDefinition updateDefinition(Long id, ApiDefinitionRequest req) {
        ApiDefinition def = getDefinition(id);
        def.setName(req.getName());
        def.setTheme(req.getTheme());
        def.setDescription(req.getDescription());
        def.setUpdatedBy(req.getUpdatedBy());
        return definitionRepository.save(def);
    }

    @Transactional
    public void deleteDefinition(Long id) {
        definitionRepository.deleteById(id);
    }

    public List<ApiVersion> listVersions(Long apiId) {
        return versionRepository.findByApiIdOrderByVersionNoDesc(apiId);
    }

    public ApiVersion getVersion(Long versionId) {
        return versionRepository.findById(versionId)
                .orElseThrow(() -> new BusinessException("版本不存在"));
    }

    @Transactional
    public ApiVersion createVersion(Long apiId, ApiVersionRequest req) {
        getDefinition(apiId);
        int nextVersion = versionRepository.findByApiIdOrderByVersionNoDesc(apiId).stream()
                .mapToInt(ApiVersion::getVersionNo)
                .max()
                .orElse(0) + 1;
        ApiVersion version = new ApiVersion();
        version.setApiId(apiId);
        version.setVersionNo(nextVersion);
        applyVersion(version, req);
        return versionRepository.save(version);
    }

    @Transactional
    public ApiVersion updateVersion(Long versionId, ApiVersionRequest req) {
        ApiVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new BusinessException("版本不存在"));
        if (version.getStatus() == PublishStatus.PUBLISHED) {
            throw new BusinessException("已发布版本请新建版本号修改，勿直接编辑");
        }
        applyVersion(version, req);
        return versionRepository.save(version);
    }

    @Transactional
    public ApiVersion publish(Long versionId, String operator) {
        ApiVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new BusinessException("版本不存在"));
        version.setStatus(PublishStatus.PUBLISHED);
        version.setPublishedAt(LocalDateTime.now());
        version.setUpdatedBy(operator);
        return versionRepository.save(version);
    }

    @Transactional
    public ApiVersion deprecate(Long versionId, String operator) {
        ApiVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new BusinessException("版本不存在"));
        version.setStatus(PublishStatus.DEPRECATED);
        version.setUpdatedBy(operator);
        return versionRepository.save(version);
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
        version.setParamSchema(req.getParamSchema() != null ? req.getParamSchema() : Map.of());
        version.setResponseMode(req.getResponseMode());
        version.setResponseConfig(req.getResponseConfig() != null ? req.getResponseConfig() : defaultResponseConfig(req));
        version.setCreatedBy(req.getCreatedBy());
        version.setUpdatedBy(req.getUpdatedBy());
    }

    private Map<String, Object> defaultResponseConfig(ApiVersionRequest req) {
        Map<String, Object> config = new HashMap<>();
        config.put("timeoutSec", 60);
        config.put("ipWhitelist", List.of());
        config.put("defaultPageSize", 20);
        config.put("maxPageSize", 500);
        config.put("maxOffset", 100000);
        config.put("chunkSize", 1000);
        config.put("maxChunkSize", 10000);
        config.put("maxTotalRows", 500000);
        config.put("streamBatchSize", 500);
        config.put("maxStreamRows", 100000);
        config.put("maxStreamDurationSec", 300);
        return config;
    }
}
