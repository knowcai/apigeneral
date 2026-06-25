package com.apigateway.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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

    /** 使用服务端 pepper（全局盐）的 HMAC-SHA256，防止彩虹表攻击。 */
    public static String hashKey(String rawKey, String pepper) {
        if (pepper == null || pepper.isBlank()) {
            return legacyHashKey(rawKey);
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(rawKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 旧版无盐 SHA-256，仅用于兼容已有 Key。 */
    public static String legacyHashKey(String rawKey) {
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

