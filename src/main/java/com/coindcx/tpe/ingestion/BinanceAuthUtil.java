package com.coindcx.tpe.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

public class BinanceAuthUtil {
    private static final Logger logger = LoggerFactory.getLogger(BinanceAuthUtil.class);
    private static final String HMAC_SHA256 = "HmacSHA256";

    public static String sign(String queryString, String secret) {
        try {
            Mac sha256Hmac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            sha256Hmac.init(secretKeySpec);
            
            byte[] hash = sha256Hmac.doFinal(queryString.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            logger.error("Failed to sign query string", e);
            throw new RuntimeException("HMAC signing failed", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
