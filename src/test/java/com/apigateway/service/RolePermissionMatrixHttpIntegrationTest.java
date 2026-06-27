package com.apigateway.service;

import com.apigateway.dto.ThemeResponse;
import com.apigateway.entity.SysUser;
import com.apigateway.entity.UserRole;
import com.apigateway.support.GatewayTestFixtures;
import com.apigateway.support.TestFixtures;
import com.apigateway.support.TestHttpAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.apigateway.support.TestHttpAuth.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 按角色 × 功能覆盖主要 Admin HTTP 接口。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RolePermissionMatrixHttpIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestFixtures fixtures;
    @Autowired
    private GatewayTestFixtures gatewayFixtures;

    private SysUser themeAdmin;
    private SysUser themeMember;
    private SysUser viewer;
    private ThemeResponse theme;
    private Long publishedApiId;

    @BeforeEach
    void setUp() {
        themeAdmin = fixtures.createUser("http_perm_admin", UserRole.API_EDITOR);
        themeMember = fixtures.createUser("http_perm_member", UserRole.API_EDITOR);
        viewer = fixtures.createUser("http_perm_viewer", UserRole.API_VIEWER);
        theme = fixtures.createThemeWithAdmins("HTTP权限主题", List.of(themeAdmin.getId()));
        fixtures.assignMembers(theme.getId(), List.of(themeMember.getId(), viewer.getId()));
        var ds = gatewayFixtures.createDatasource(theme.getId(), "http-perm-ds");
        publishedApiId = gatewayFixtures.publishApi(theme.getId(), "http-perm-api", ds.getId()).definition().getId();
    }

    @Nested
    class Theme {

        @Test
        void superAdminCanPostTheme() throws Exception {
            String token = TestHttpAuth.login(mockMvc, "testadmin", "testadmin123");
            mockMvc.perform(post("/admin/themes")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"http-new-theme","enabled":true,
                                    "members":[{"userId":%d,"role":"THEME_ADMIN"}]}
                                    """.formatted(themeAdmin.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }

        @Test
        void themeAdminCannotPostTheme() throws Exception {
            String token = TestHttpAuth.login(mockMvc, themeAdmin.getUsername(), "password123");
            mockMvc.perform(post("/admin/themes")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"http-forbidden-theme","enabled":true,"members":[]}
                                    """))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class ThemeApiKey {

        @Test
        void superAdminPostKeyForbidden() throws Exception {
            String token = TestHttpAuth.login(mockMvc, "testadmin", "testadmin123");
            mockMvc.perform(post("/admin/themes/" + theme.getId() + "/api-keys")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"http-sa-key\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(403));
        }

        @Test
        void themeAdminPostKeyAccepted() throws Exception {
            String token = TestHttpAuth.login(mockMvc, themeAdmin.getUsername(), "password123");
            mockMvc.perform(post("/admin/themes/" + theme.getId() + "/api-keys")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"http-admin-key\"}"))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.code").value(202));
        }

        @Test
        void themeMemberPostKeyForbidden() throws Exception {
            String token = TestHttpAuth.login(mockMvc, themeMember.getUsername(), "password123");
            mockMvc.perform(post("/admin/themes/" + theme.getId() + "/api-keys")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"http-member-key\"}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class Datasource {

        @Test
        void superAdminPostDatasourceOk() throws Exception {
            String token = TestHttpAuth.login(mockMvc, "testadmin", "testadmin123");
            mockMvc.perform(post("/admin/datasources")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(datasourceJson("http-sa-ds")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }

        @Test
        void themeMemberPostDatasourceAccepted() throws Exception {
            String token = TestHttpAuth.login(mockMvc, themeMember.getUsername(), "password123");
            mockMvc.perform(post("/admin/datasources")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(datasourceJson("http-member-ds")))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.code").value(202));
        }
    }

    @Nested
    class Api {

        @Test
        void superAdminPostApiOk() throws Exception {
            String token = TestHttpAuth.login(mockMvc, "testadmin", "testadmin123");
            mockMvc.perform(post("/admin/apis")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(apiJson("http-sa-api")))
                    .andExpect(status().isOk());
        }

        @Test
        void themeMemberPostApiAccepted() throws Exception {
            String token = TestHttpAuth.login(mockMvc, themeMember.getUsername(), "password123");
            mockMvc.perform(post("/admin/apis")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(apiJson("http-member-api")))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.code").value(202));
        }

        @Test
        void viewerPostApiForbidden() throws Exception {
            String token = TestHttpAuth.login(mockMvc, viewer.getUsername(), "password123");
            mockMvc.perform(post("/admin/apis")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(apiJson("http-viewer-api")))
                    .andExpect(status().isForbidden());
        }

        @Test
        void viewerCanGetApis() throws Exception {
            String token = TestHttpAuth.login(mockMvc, viewer.getUsername(), "password123");
            mockMvc.perform(get("/admin/apis").header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[?(@.apiCode=='http-perm-api')]").exists());
        }

        @Test
        void themeMemberDeleteApiForbidden() throws Exception {
            String token = TestHttpAuth.login(mockMvc, themeMember.getUsername(), "password123");
            mockMvc.perform(delete("/admin/apis/" + publishedApiId).header("Authorization", bearer(token)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class GatewayPolicy {

        @Test
        void superAdminCanPutPolicy() throws Exception {
            String token = TestHttpAuth.login(mockMvc, "testadmin", "testadmin123");
            mockMvc.perform(put("/admin/gateway-policy")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(policyJson()))
                    .andExpect(status().isOk());
        }

        @Test
        void themeAdminPutPolicyForbidden() throws Exception {
            String token = TestHttpAuth.login(mockMvc, themeAdmin.getUsername(), "password123");
            mockMvc.perform(put("/admin/gateway-policy")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(policyJson()))
                    .andExpect(status().isForbidden());
        }

        @Test
        void themeAdminCanGetPolicy() throws Exception {
            String token = TestHttpAuth.login(mockMvc, themeAdmin.getUsername(), "password123");
            mockMvc.perform(get("/admin/gateway-policy").header("Authorization", bearer(token)))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    class Platform {

        @Test
        void superAdminCanListUsers() throws Exception {
            String token = TestHttpAuth.login(mockMvc, "testadmin", "testadmin123");
            mockMvc.perform(get("/admin/users").header("Authorization", bearer(token)))
                    .andExpect(status().isOk());
        }

        @Test
        void editorCannotListUsers() throws Exception {
            String token = TestHttpAuth.login(mockMvc, themeAdmin.getUsername(), "password123");
            mockMvc.perform(get("/admin/users").header("Authorization", bearer(token)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void editorCannotListConsumers() throws Exception {
            String token = TestHttpAuth.login(mockMvc, themeAdmin.getUsername(), "password123");
            mockMvc.perform(get("/admin/consumers").header("Authorization", bearer(token)))
                    .andExpect(status().isForbidden());
        }
    }

    private String datasourceJson(String name) {
        return """
                {"name":"%s","type":"POSTGRES","host":"localhost","port":5432,
                "databaseName":"test","username":"u","password":"p","themeId":%d}
                """.formatted(name, theme.getId());
    }

    private String apiJson(String code) {
        return """
                {"apiCode":"%s","name":"%s","themeId":%d}
                """.formatted(code, code, theme.getId());
    }

    private String policyJson() {
        return """
                {"globalQpsEnabled":true,"globalQps":800,"ipQpsEnabled":true,"ipQps":80,
                "apiQpsEnabled":true,"apiQps":40,"circuitEnabled":true,"circuitFailureRate":50,
                "circuitMinCalls":10,"circuitWaitSec":30,"retryEnabled":false,"retryMaxAttempts":1,"retryIntervalMs":100}
                """;
    }
}
