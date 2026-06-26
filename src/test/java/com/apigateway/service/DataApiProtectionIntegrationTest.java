package com.apigateway.service;

import com.apigateway.dto.QueryResult;
import com.apigateway.dto.ThemeResponse;
import com.apigateway.entity.SysUser;
import com.apigateway.entity.UserRole;
import com.apigateway.support.GatewayTestFixtures;
import com.apigateway.support.TestAuth;
import com.apigateway.support.TestFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DataApiProtectionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestFixtures fixtures;
    @Autowired
    private GatewayTestFixtures gatewayFixtures;
    @MockBean
    private SqlExecutionService sqlExecutionService;

    private ThemeResponse theme;
    private String apiKey;
    private String dataUrl;

    @BeforeEach
    void setUp() {
        SysUser admin = fixtures.createUser("protect_admin", UserRole.API_EDITOR);
        theme = fixtures.createThemeWithAdmins("保护策略测试主题", List.of(admin.getId()));
        var datasource = gatewayFixtures.createDatasource(theme.getId(), "protect-ds");
        var published = gatewayFixtures.publishApi(theme.getId(), "protect-api", datasource.getId());
        apiKey = gatewayFixtures.createThemeApiKey(theme.getId(), "protect-key").getApiKey();
        dataUrl = "/api/data/v" + published.version().getVersionNo()
                + "/" + published.themeCode() + "/" + published.definition().getApiCode();

        when(sqlExecutionService.execute(any(), anyString(), anyMap(), anyMap(), anyInt(), anyInt()))
                .thenReturn(QueryResult.builder()
                        .rows(List.of(Map.of("id", 1)))
                        .total(1)
                        .page(1)
                        .pageSize(10)
                        .hasMore(false)
                        .build());
        TestAuth.clear();
    }

    @AfterEach
    void tearDown() {
        gatewayFixtures.resetGatewayPolicy();
        TestAuth.clear();
    }

    @Test
    void apiQpsExceeded_returns429() throws Exception {
        gatewayFixtures.applyApiRateLimitOnly(1);
        TestAuth.clear();

        mockMvc.perform(get(dataUrl)
                        .header("X-Api-Key", apiKey)
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk());

        mockMvc.perform(get(dataUrl)
                        .header("X-Api-Key", apiKey)
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(429));
    }

    @Test
    void circuitOpen_returns503() throws Exception {
        gatewayFixtures.applyCircuitBreakerOnly(2, 50);
        when(sqlExecutionService.execute(any(), anyString(), anyMap(), anyMap(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("simulated db failure"));
        TestAuth.clear();

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get(dataUrl)
                            .header("X-Api-Key", apiKey)
                            .param("page", "1")
                            .param("pageSize", "10"))
                    .andExpect(status().is5xxServerError());
        }

        mockMvc.perform(get(dataUrl)
                        .header("X-Api-Key", apiKey)
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503));
    }
}
