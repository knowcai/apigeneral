package com.apigateway.service;

import com.apigateway.entity.SysUser;
import com.apigateway.entity.UserRole;
import com.apigateway.support.TestFixtures;
import com.apigateway.support.TestHttpAuth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static com.apigateway.support.TestHttpAuth.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminHttpSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestFixtures fixtures;

    @Test
    void adminWithoutJwt_returns401() throws Exception {
        mockMvc.perform(get("/admin/themes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidJwt_returns401() throws Exception {
        mockMvc.perform(get("/admin/themes").header("Authorization", bearer("invalid.token.value")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginSuccess_accessThemes() throws Exception {
        String token = TestHttpAuth.login(mockMvc, "testadmin", "testadmin123");
        mockMvc.perform(get("/admin/themes").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void loginWrongPassword_returns401() throws Exception {
        mockMvc.perform(post("/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"testadmin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void superAdminCanListConsumers() throws Exception {
        String token = TestHttpAuth.login(mockMvc, "testadmin", "testadmin123");
        mockMvc.perform(get("/admin/consumers").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void nonSuperAdminCannotListConsumers() throws Exception {
        SysUser editor = fixtures.createUser("admin_sec_editor", UserRole.API_EDITOR);
        String token = TestHttpAuth.login(mockMvc, editor.getUsername(), "password123");
        mockMvc.perform(get("/admin/consumers").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void authMe_returnsCurrentUser() throws Exception {
        String token = TestHttpAuth.login(mockMvc, "testadmin", "testadmin123");
        mockMvc.perform(get("/admin/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("testadmin"));
    }
}
