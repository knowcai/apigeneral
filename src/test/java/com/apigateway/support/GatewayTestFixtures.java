package com.apigateway.support;

import com.apigateway.dto.ApiDefinitionRequest;
import com.apigateway.dto.ApiVersionRequest;
import com.apigateway.dto.ConsumerCreateResponse;
import com.apigateway.dto.GatewayPolicyRequest;
import com.apigateway.dto.ThemeApiKeyRequest;
import com.apigateway.entity.*;
import com.apigateway.repository.DatasourceRepository;
import com.apigateway.repository.ThemeRepository;
import com.apigateway.service.ApiManagementService;
import com.apigateway.service.GatewayPolicyService;
import com.apigateway.service.ThemeApiKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GatewayTestFixtures {

    private final TestFixtures testFixtures;
    private final ThemeRepository themeRepository;
    private final DatasourceRepository datasourceRepository;
    private final ApiManagementService apiManagementService;
    private final ThemeApiKeyService themeApiKeyService;
    private final GatewayPolicyService gatewayPolicyService;

    public record PublishedApi(ApiDefinition definition, ApiVersion version, String themeCode) {
    }

    public Datasource createDatasource(Long themeId, String name) {
        Datasource ds = new Datasource();
        ds.setName(name);
        ds.setType(DatasourceType.POSTGRES);
        ds.setHost("localhost");
        ds.setPort(5432);
        ds.setDatabaseName("test");
        ds.setUsername("test");
        ds.setPassword("test");
        ds.setThemeId(themeId);
        return datasourceRepository.save(ds);
    }

    public PublishedApi publishApi(Long themeId, String apiCode, Long datasourceId) {
        loginSuperAdmin();
        Theme theme = themeRepository.findById(themeId).orElseThrow();
        ApiDefinitionRequest defReq = new ApiDefinitionRequest();
        defReq.setApiCode(apiCode);
        defReq.setName(apiCode);
        defReq.setThemeId(themeId);
        ApiDefinition def = apiManagementService.createDefinitionDirect(defReq, theme);

        ApiVersionRequest verReq = new ApiVersionRequest();
        verReq.setDatasourceId(datasourceId);
        verReq.setSqlTemplate("SELECT 1 AS id");
        ApiVersion version = apiManagementService.createVersionDirect(def.getId(), verReq);
        apiManagementService.publishDirect(version.getId(), "testadmin");
        return new PublishedApi(def, version, theme.getCode());
    }

    public ConsumerCreateResponse createThemeApiKey(Long themeId, String name) {
        loginSuperAdmin();
        ThemeApiKeyRequest req = new ThemeApiKeyRequest();
        req.setName(name);
        return themeApiKeyService.createDirect(themeId, req);
    }

    /** 仅启用 API 级 QPS=1，关闭熔断与重试，便于限流测试。 */
    public void applyApiRateLimitOnly(int apiQps) {
        loginSuperAdmin();
        GatewayPolicyRequest req = basePolicyRequest();
        req.setGlobalQpsEnabled(false);
        req.setIpQpsEnabled(false);
        req.setApiQpsEnabled(true);
        req.setApiQps(apiQps);
        req.setCircuitEnabled(false);
        req.setRetryEnabled(false);
        gatewayPolicyService.update(req);
    }

    /** 关闭限流，低阈值熔断，关闭重试，便于熔断 HTTP 测试。 */
    public void applyCircuitBreakerOnly(int minCalls, int failureRatePercent) {
        loginSuperAdmin();
        GatewayPolicyRequest req = basePolicyRequest();
        req.setGlobalQpsEnabled(false);
        req.setIpQpsEnabled(false);
        req.setApiQpsEnabled(false);
        req.setCircuitEnabled(true);
        req.setCircuitMinCalls(minCalls);
        req.setCircuitFailureRate(failureRatePercent);
        req.setCircuitWaitSec(60);
        req.setRetryEnabled(false);
        gatewayPolicyService.update(req);
    }

    public void resetGatewayPolicy() {
        loginSuperAdmin();
        gatewayPolicyService.update(basePolicyRequest());
    }

    private GatewayPolicyRequest basePolicyRequest() {
        GatewayPolicyRequest req = new GatewayPolicyRequest();
        req.setGlobalQpsEnabled(true);
        req.setGlobalQps(1000);
        req.setIpQpsEnabled(true);
        req.setIpQps(100);
        req.setApiQpsEnabled(true);
        req.setApiQps(50);
        req.setCircuitEnabled(true);
        req.setCircuitFailureRate(50);
        req.setCircuitMinCalls(20);
        req.setCircuitWaitSec(30);
        req.setRetryEnabled(true);
        req.setRetryMaxAttempts(2);
        req.setRetryIntervalMs(500);
        return req;
    }

    private void loginSuperAdmin() {
        SysUser admin = testFixtures.requireSuperAdmin();
        TestAuth.login(admin.getId(), admin.getUsername(), UserRole.SUPER_ADMIN);
    }
}
