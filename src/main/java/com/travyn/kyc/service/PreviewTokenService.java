package com.travyn.kyc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Generates and validates short-lived (15-min) signed preview tokens
 * used during Aadhaar-first registration.
 *
 * Format: Base64(JSON payload) + "." + Base64(HMAC-SHA256 signature)
 * The JSON payload includes an "exp" field (Unix epoch seconds).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PreviewTokenService {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    private static final long EXPIRY_SECONDS = 15 * 60; // 15 minutes
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String generateToken(String name, String gender, String dob, String aadhaarLast4) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("name", name);
            payload.put("gender", gender);
            payload.put("dob", dob);
            payload.put("last4", aadhaarLast4);
            payload.put("exp", Instant.now().getEpochSecond() + EXPIRY_SECONDS);

            String json = objectMapper.writeValueAsString(payload);
            String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(json.getBytes(StandardCharsets.UTF_8));

            String signature = hmacSha256(encodedPayload, jwtSecret);

            return encodedPayload + "." + signature;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate preview token", e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> validateAndDecode(String token) {
        if (token == null || !token.contains(".")) {
            throw new IllegalArgumentException("Invalid preview token format");
        }

        String[] parts = token.split("\\.", 2);
        String encodedPayload = parts[0];
        String providedSignature = parts[1];

        // Verify signature
        String expectedSignature = hmacSha256(encodedPayload, jwtSecret);
        if (!constantTimeEquals(expectedSignature, providedSignature)) {
            throw new IllegalArgumentException("Preview token signature is invalid");
        }

        // Decode payload
        try {
            String json = new String(Base64.getUrlDecoder().decode(encodedPayload), StandardCharsets.UTF_8);
            Map<String, Object> payload = objectMapper.readValue(json, Map.class);

            // Check expiry
            long exp = ((Number) payload.get("exp")).longValue();
            if (Instant.now().getEpochSecond() > exp) {
                throw new IllegalArgumentException("Preview token has expired. Please scan your Aadhaar QR again.");
            }

            return payload;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to decode preview token", e);
        }
    }

    private String hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 failed", e);
        }
    }

    /** Constant-time string comparison to prevent timing attacks */
    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
