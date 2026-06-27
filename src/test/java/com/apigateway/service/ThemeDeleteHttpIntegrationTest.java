package com.apigateway.service;

import com.apigateway.dto.ThemeResponse;
import com.apigateway.entity.SysUser;
import com.apigateway.entity.UserRole;
import com.apigateway.support.GatewayTestFixtures;
import com.apigateway.support.TestFixtures;
import com.apigateway.support.TestHttpAuth;
import org.junit.jupiter.api.BeforeEach;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ThemeDeleteHttpIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestFixtures fixtures;
    @Autowired
    private GatewayTestFixtures gatewayFixtures;

    private SysUser themeAdmin;
    private ThemeResponse theme;

    @BeforeEach
    void setUp() {
        themeAdmin = fixtures.createUser("del_http_admin", UserRole.API_EDITOR);
        theme = fixtures.createThemeWithAdmins("HTTP 删除测试", List.of(themeAdmin.getId()));
    }

    @Test
    void superAdminDeleteThemeViaHttp() throws Exception {
        String token = TestHttpAuth.login(mockMvc, "testadmin", "testadmin123");
        mockMvc.perform(delete("/admin/themes/" + theme.getId()).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void themeAdminDeleteThemeViaHttp_forbidden() throws Exception {
        String token = TestHttpAuth.login(mockMvc, themeAdmin.getUsername(), "password123");
        mockMvc.perform(delete("/admin/themes/" + theme.getId()).header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteThemeWithApisViaHttp_badRequest() throws Exception {
        var ds = gatewayFixtures.createDatasource(theme.getId(), "ds-del-http");
        gatewayFixtures.publishApi(theme.getId(), "api-del-http", ds.getId());

        String token = TestHttpAuth.login(mockMvc, "testadmin", "testadmin123");
        mockMvc.perform(delete("/admin/themes/" + theme.getId()).header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void keyCreateClaimFlowViaHttp() throws Exception {
        String adminToken = TestHttpAuth.login(mockMvc, themeAdmin.getUsername(), "password123");
        mockMvc.perform(post("/admin/themes/" + theme.getId() + "/api-keys")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"http-flow-key\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value(202));

        String saToken = TestHttpAuth.login(mockMvc, "testadmin", "testadmin123");
        mockMvc.perform(post("/admin/themes/" + theme.getId() + "/api-keys")
                        .header("Authorization", bearer(saToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"http-direct-key\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/admin/themes/" + theme.getId() + "/api-keys")
                        .header("Authorization", bearer(saToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usedSlots").value(1))
                .andExpect(jsonPath("$.data.maxSlots").value(5))
                .andExpect(jsonPath("$.data.canManage").value(false));
    }
}
