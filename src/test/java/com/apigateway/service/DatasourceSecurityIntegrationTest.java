package com.apigateway.service;

import com.apigateway.dto.DatasourceRequest;
import com.apigateway.dto.DatasourceResponse;
import com.apigateway.entity.DatasourceType;
import com.apigateway.entity.UserRole;
import com.apigateway.repository.DatasourceRepository;
import com.apigateway.security.SecretCryptoService;
import com.apigateway.support.TestAuth;
import com.apigateway.support.TestFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class DatasourceSecurityIntegrationTest {

    @Autowired
    private DatasourceService datasourceService;
    @Autowired
    private DatasourceRepository datasourceRepository;
    @Autowired
    private SecretCryptoService cryptoService;
    @Autowired
    private TestFixtures fixtures;

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    @Test
    void passwordEncryptedAtRestAndNeverReturned() {
        var admin = fixtures.requireSuperAdmin();
        TestAuth.login(admin.getId(), admin.getUsername(), UserRole.SUPER_ADMIN);

        DatasourceRequest req = new DatasourceRequest();
        req.setName("enc-ds");
        req.setType(DatasourceType.POSTGRES);
        req.setHost("localhost");
        req.setPort(5432);
        req.setDatabaseName("test");
        req.setUsername("u");
        req.setPassword("plain-secret");
        req.setThemeId(fixtures.createThemeWithAdmins("加密主题", List.of(admin.getId())).getId());

        DatasourceResponse created = datasourceService.create(req);
        var entity = datasourceRepository.findById(created.getId()).orElseThrow();
        assertTrue(cryptoService.isEncrypted(entity.getPassword()));
        assertEquals("plain-secret", cryptoService.decrypt(entity.getPassword()));

        DatasourceResponse fetched = datasourceService.getResponse(created.getId());
        assertTrue(fetched.isPasswordConfigured());
    }
}
