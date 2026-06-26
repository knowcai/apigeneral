package com.apigateway.service;

import com.apigateway.entity.Datasource;
import com.apigateway.security.SecretCryptoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DatasourcePasswordResolver {

    private final SecretCryptoService cryptoService;

    public String resolvePlainPassword(Datasource ds) {
        return cryptoService.decrypt(ds.getPassword());
    }

    public String encryptForStorage(String plain) {
        return cryptoService.encrypt(plain);
    }
}
