package com.apigateway.security;

import com.apigateway.config.SecretProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecretCryptoServiceTest {

    private SecretCryptoService cryptoService;

    @BeforeEach
    void setUp() {
        SecretProperties properties = new SecretProperties();
        properties.setEncryptionKey("test-encryption-key-for-unit-tests!!");
        cryptoService = new SecretCryptoService(properties);
    }

    @Test
    void encryptDecryptRoundTrip() {
        String plain = "s3cret-p@ss";
        String stored = cryptoService.encrypt(plain);
        assertTrue(stored.startsWith("ENC:"));
        assertNotEquals(plain, stored);
        assertEquals(plain, cryptoService.decrypt(stored));
    }

    @Test
    void legacyPlaintextStillWorks() {
        assertEquals("legacy", cryptoService.decrypt("legacy"));
    }

    @Test
    void doubleEncryptIsIdempotent() {
        String once = cryptoService.encrypt("pw");
        assertEquals(once, cryptoService.encrypt(once));
    }
}
