package com.apigateway.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

public final class ApiKeySupport {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PREFIX = "gw_";

    private ApiKeySupport() {
    }

    public static String generateRawKey() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return PREFIX + HexFormat.of().formatHex(bytes);
    }

    public static String hashKey(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String keyPrefix(String rawKey) {
        if (rawKey == null || rawKey.length() <= 8) {
            return rawKey;
        }
        return rawKey.substring(0, 8) + "…";
    }
}
