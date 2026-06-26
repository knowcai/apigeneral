package com.apigateway.service;

import com.apigateway.support.TestHttpAuth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static com.apigateway.support.TestHttpAuth.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MonitoringHttpIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void monitoringDashboard_requiresAuth() throws Exception {
        mockMvc.perform(get("/admin/monitoring/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void monitoringDashboard_superAdminOk() throws Exception {
        String token = TestHttpAuth.login(mockMvc, "testadmin", "testadmin123");
        mockMvc.perform(get("/admin/monitoring/dashboard").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists());
    }
}
