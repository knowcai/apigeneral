package com.apigateway.service;

import com.apigateway.dto.QueryResult;
import com.apigateway.dto.ThemeMemberRequest;
import com.apigateway.dto.ThemeRequest;
import com.apigateway.dto.ThemeResponse;
import com.apigateway.entity.SysUser;
import com.apigateway.entity.ThemeMembershipRole;
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
class DynamicApiServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestFixtures fixtures;
    @Autowired
    private GatewayTestFixtures gatewayFixtures;
    @Autowired
    private ThemeService themeService;
    @Autowired
    private ApiManagementService apiManagementService;
    @MockBean
    private SqlExecutionService sqlExecutionService;

    private ThemeResponse theme;
    private GatewayTestFixtures.PublishedApi publishedApi;
    private String apiKey;
    private String dataUrl;
    private SysUser themeAdmin;

    @BeforeEach
    void setUp() {
        themeAdmin = fixtures.createUser("data_api_admin", UserRole.API_EDITOR);
        theme = fixtures.createThemeWithAdmins("Data API 测试主题", List.of(themeAdmin.getId()));
        var datasource = gatewayFixtures.createDatasource(theme.getId(), "data-api-ds");
        publishedApi = gatewayFixtures.publishApi(theme.getId(), "data-api-test", datasource.getId());
        apiKey = gatewayFixtures.createThemeApiKey(theme.getId(), "data-api-key").getApiKey();
        dataUrl = "/api/data/v" + publishedApi.version().getVersionNo()
                + "/" + publishedApi.themeCode() + "/" + publishedApi.definition().getApiCode();

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
        TestAuth.clear();
    }

    @Test
    void noApiKey_returns401() throws Exception {
        mockMvc.perform(get(dataUrl).param("page", "1").param("pageSize", "10"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void invalidApiKey_returns401() throws Exception {
        mockMvc.perform(get(dataUrl)
                        .header("X-Api-Key", "gw_invalid_key_00000000000000000000000000000000")
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void validThemeKey_returns200() throws Exception {
        mockMvc.perform(get(dataUrl)
                        .header("Authorization", "Bearer " + apiKey)
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.rows[0].id").value(1))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void disabledTheme_returns403() throws Exception {
        TestAuth.login(fixtures.requireSuperAdmin().getId(), "testadmin", UserRole.SUPER_ADMIN);
        ThemeRequest disable = new ThemeRequest();
        disable.setName(theme.getName());
        disable.setEnabled(false);
        ThemeMemberRequest adminMember = new ThemeMemberRequest();
        adminMember.setUserId(themeAdmin.getId());
        adminMember.setRole(ThemeMembershipRole.THEME_ADMIN);
        disable.setMembers(List.of(adminMember));
        themeService.update(theme.getId(), disable);
        TestAuth.clear();

        mockMvc.perform(get(dataUrl)
                        .header("X-Api-Key", apiKey)
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void keyWithoutAccessToOtherThemeApi_returns403() throws Exception {
        SysUser otherAdmin = fixtures.createUser("other_theme_admin", UserRole.API_EDITOR);
        ThemeResponse otherTheme = fixtures.createThemeWithAdmins("其他主题", List.of(otherAdmin.getId()));
        var otherDs = gatewayFixtures.createDatasource(otherTheme.getId(), "other-ds");
        var otherApi = gatewayFixtures.publishApi(otherTheme.getId(), "other-api", otherDs.getId());
        String otherUrl = "/api/data/v" + otherApi.version().getVersionNo()
                + "/" + otherApi.themeCode() + "/" + otherApi.definition().getApiCode();
        TestAuth.clear();

        mockMvc.perform(get(otherUrl)
                        .header("X-Api-Key", apiKey)
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void xApiKeyHeader_returns200() throws Exception {
        mockMvc.perform(get(dataUrl)
                        .header("X-Api-Key", apiKey)
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.rows[0].id").value(1));
    }

    @Test
    void themePathMismatch_returns400() throws Exception {
        String wrongThemeUrl = "/api/data/v" + publishedApi.version().getVersionNo()
                + "/99999/" + publishedApi.definition().getApiCode();
        mockMvc.perform(get(wrongThemeUrl)
                        .header("X-Api-Key", apiKey)
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("主题路径不匹配")));
    }

    @Test
    void suspendedVersion_returns400() throws Exception {
        TestAuth.login(fixtures.requireSuperAdmin().getId(), "testadmin", UserRole.SUPER_ADMIN);
        apiManagementService.suspendDirect(publishedApi.version().getId());
        TestAuth.clear();

        mockMvc.perform(get(dataUrl)
                        .header("X-Api-Key", apiKey)
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("暂停")));
    }

    @Test
    void missingPagination_returns400() throws Exception {
        mockMvc.perform(get(dataUrl).header("X-Api-Key", apiKey))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }
}
