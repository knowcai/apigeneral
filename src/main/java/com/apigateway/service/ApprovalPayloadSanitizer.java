package com.apigateway.service;

import com.apigateway.dto.DatasourceRequest;
import com.apigateway.entity.ApprovalResourceType;
import com.apigateway.security.SecretCryptoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ApprovalPayloadSanitizer {

    static final String SECRETS_KEY = "_secrets";

    private final ObjectMapper objectMapper;
    private final SecretCryptoService cryptoService;

    public Object sanitizeForStorage(ApprovalResourceType type, Object payload) {
        if (type != ApprovalResourceType.DATASOURCE) {
            return payload;
        }
        DatasourceRequest req = objectMapper.convertValue(payload, DatasourceRequest.class);
        Map<String, Object> map = objectMapper.convertValue(req, Map.class);
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            map.remove("password");
            map.put("passwordConfigured", true);
            Map<String, String> secrets = new LinkedHashMap<>();
            secrets.put("password", cryptoService.encrypt(req.getPassword()));
            map.put(SECRETS_KEY, secrets);
        }
        return map;
    }

    public Object restoreForApply(ApprovalResourceType type, Object payload) {
        if (type != ApprovalResourceType.DATASOURCE || !(payload instanceof Map<?, ?> raw)) {
            return payload;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        raw.forEach((k, v) -> map.put(String.valueOf(k), v));
        Object secrets = map.remove(SECRETS_KEY);
        if (secrets instanceof Map<?, ?> secretMap) {
            Object enc = secretMap.get("password");
            if (enc != null) {
                map.put("password", cryptoService.decrypt(String.valueOf(enc)));
            }
        }
        map.remove("passwordConfigured");
        return map;
    }

    public String redactForDisplay(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return payloadJson;
        }
        try {
            Object parsed = objectMapper.readValue(payloadJson, Object.class);
            if (parsed instanceof Map<?, ?> map) {
                Map<String, Object> copy = new LinkedHashMap<>();
                map.forEach((k, v) -> {
                    String key = String.valueOf(k);
                    if (SECRETS_KEY.equals(key)) {
                        return;
                    }
                    if ("password".equals(key)) {
                        copy.put("password", "********");
                        return;
                    }
                    copy.put(key, v);
                });
                return objectMapper.writeValueAsString(copy);
            }
            return payloadJson;
        } catch (Exception e) {
            return payloadJson;
        }
    }
}
