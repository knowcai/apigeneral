package com.apigateway.service;

import com.apigateway.config.SecretProperties;
import com.apigateway.dto.DatasourceRequest;
import com.apigateway.entity.ApprovalResourceType;
import com.apigateway.security.SecretCryptoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApprovalPayloadSanitizerTest {

    private ApprovalPayloadSanitizer sanitizer;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        SecretProperties props = new SecretProperties();
        props.setEncryptionKey("test-encryption-key-for-unit-tests!!");
        sanitizer = new ApprovalPayloadSanitizer(objectMapper, new SecretCryptoService(props));
    }

    @Test
    void redactsPasswordInDisplayAndRestoresForApply() throws Exception {
        DatasourceRequest req = new DatasourceRequest();
        req.setName("ds");
        req.setPassword("secret-plain");

        Object stored = sanitizer.sanitizeForStorage(ApprovalResourceType.DATASOURCE, req);
        String json = objectMapper.writeValueAsString(stored);
        assertFalse(json.contains("secret-plain"));
        assertTrue(json.contains("passwordConfigured"));

        String display = sanitizer.redactForDisplay(json);
        assertFalse(display.contains("secret-plain"));
        assertFalse(display.contains("_secrets"));

        Object restored = sanitizer.restoreForApply(ApprovalResourceType.DATASOURCE, objectMapper.readValue(json, Object.class));
        DatasourceRequest applied = objectMapper.convertValue(restored, DatasourceRequest.class);
        assertEquals("secret-plain", applied.getPassword());
    }
}
