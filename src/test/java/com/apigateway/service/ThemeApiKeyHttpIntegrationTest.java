package com.apigateway.service;

import com.apigateway.dto.ThemeResponse;
import com.apigateway.entity.SysUser;
import com.apigateway.entity.UserRole;
import com.apigateway.repository.ConsumerRepository;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ThemeApiKeyHttpIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestFixtures fixtures;
    @Autowired
    private GatewayTestFixtures gatewayFixtures;
    @Autowired
    private ConsumerRepository consumerRepository;

    private SysUser themeAdmin;
    private SysUser themeAdmin2;
    private ThemeResponse theme;

    @BeforeEach
    void setUp() {
        themeAdmin = fixtures.createUser("http_key_admin", UserRole.API_EDITOR);
        themeAdmin2 = fixtures.createUser("http_key_admin2", UserRole.API_EDITOR);
        theme = fixtures.createThemeWithAdmins("HTTP Key 测试主题",
                List.of(themeAdmin.getId(), themeAdmin2.getId()));
    }

    @Test
    void superAdminCreateApiKeyViaHttp_forbidden() throws Exception {
        String token = TestHttpAuth.login(mockMvc, "testadmin", "testadmin123");
        mockMvc.perform(post("/admin/themes/" + theme.getId() + "/api-keys")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"http-direct-key\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void themeAdminCreateApiKeyViaHttp_returns202() throws Exception {
        String token = TestHttpAuth.login(mockMvc, themeAdmin.getUsername(), "password123");
        mockMvc.perform(post("/admin/themes/" + theme.getId() + "/api-keys")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"http-approval-key\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value(202));
        assertEquals(0, consumerRepository.countByThemeId(theme.getId()));
    }

    @Test
    void listApiKeysViaHttp_superAdminReadOnly() throws Exception {
        gatewayFixtures.createThemeApiKey(theme.getId(), "http-list-key");
        String token = TestHttpAuth.login(mockMvc, "testadmin", "testadmin123");
        mockMvc.perform(get("/admin/themes/" + theme.getId() + "/api-keys")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.keys[0].name").value("http-list-key"))
                .andExpect(jsonPath("$.data.keys[0].keyPrefix").isNotEmpty())
                .andExpect(jsonPath("$.data.usedSlots").value(1))
                .andExpect(jsonPath("$.data.maxSlots").value(5))
                .andExpect(jsonPath("$.data.canManage").value(false));
    }

    @Test
    void maxKeysViaHttp_returns400() throws Exception {
        for (int i = 0; i < ThemeApiKeyService.MAX_KEYS_PER_THEME; i++) {
            gatewayFixtures.createThemeApiKey(theme.getId(), "max-key-" + i);
        }
        String token = TestHttpAuth.login(mockMvc, themeAdmin.getUsername(), "password123");
        mockMvc.perform(post("/admin/themes/" + theme.getId() + "/api-keys")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"overflow\"}"))
                .andExpect(status().isBadRequest());
    }
}
