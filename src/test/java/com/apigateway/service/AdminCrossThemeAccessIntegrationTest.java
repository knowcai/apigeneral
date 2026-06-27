package com.apigateway.service;

import com.apigateway.dto.ThemeResponse;
import com.apigateway.entity.SysUser;
import com.apigateway.entity.UserRole;
import com.apigateway.support.GatewayTestFixtures;
import com.apigateway.support.TestAuth;
import com.apigateway.support.TestFixtures;
import com.apigateway.support.TestHttpAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.apigateway.support.TestHttpAuth.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminCrossThemeAccessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestFixtures fixtures;
    @Autowired
    private GatewayTestFixtures gatewayFixtures;

    private SysUser themeAdminA;
    private ThemeResponse themeA;
    private ThemeResponse themeB;
    private Long foreignApiId;

    @BeforeEach
    void setUp() {
        themeAdminA = fixtures.createUser("idor_admin_a", UserRole.API_EDITOR);
        SysUser themeAdminB = fixtures.createUser("idor_admin_b", UserRole.API_EDITOR);
        themeA = fixtures.createThemeWithAdmins("IDOR-A", List.of(themeAdminA.getId()));
        themeB = fixtures.createThemeWithAdmins("IDOR-B", List.of(themeAdminB.getId()));
        var ds = gatewayFixtures.createDatasource(themeB.getId(), "idor-ds");
        var published = gatewayFixtures.publishApi(themeB.getId(), "idor_api", ds.getId());
        foreignApiId = published.definition().getId();
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    @Test
    void themeAdminCannotListForeignApiVersions() throws Exception {
        String token = TestHttpAuth.login(mockMvc, themeAdminA.getUsername(), "password123");
        mockMvc.perform(get("/admin/apis/" + foreignApiId + "/versions")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void themeAdminCannotExportForeignOpenApi() throws Exception {
        String token = TestHttpAuth.login(mockMvc, themeAdminA.getUsername(), "password123");
        mockMvc.perform(get("/admin/apis/by-code/idor_api/openapi")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void superAdminCanListForeignApiVersions() throws Exception {
        String token = TestHttpAuth.login(mockMvc, "testadmin", "testadmin123");
        mockMvc.perform(get("/admin/apis/" + foreignApiId + "/versions")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }
}
