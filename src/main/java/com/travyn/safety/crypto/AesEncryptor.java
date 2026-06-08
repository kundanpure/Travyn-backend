package com.travyn.safety.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM AttributeConverter for encrypting Double values (latitude/longitude) at rest.
 * The encryption key is sourced from the application property {@code location.encryption.key}.
 * Each encrypted value has a random 12-byte IV prepended to the ciphertext.
 */
@Converter
@Component
@Slf4j
public class AesEncryptor implements AttributeConverter<Double, String> {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private static byte[] keyBytes;

    @Value("${location.encryption.key}")
    public void setKey(String key) {
        // Ensure exactly 32 bytes for AES-256
        byte[] raw = key.getBytes(StandardCharsets.UTF_8);
        keyBytes = new byte[32];
        System.arraycopy(raw, 0, keyBytes, 0, Math.min(raw.length, 32));
    }

    @Override
    public String convertToDatabaseColumn(Double attribute) {
        if (attribute == null) return null;
        try {
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] plaintext = attribute.toString().getBytes(StandardCharsets.UTF_8);
            byte[] ciphertext = cipher.doFinal(plaintext);

            // Prepend IV to ciphertext
            ByteBuffer byteBuffer = ByteBuffer.allocate(GCM_IV_LENGTH + ciphertext.length);
            byteBuffer.put(iv);
            byteBuffer.put(ciphertext);

            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            log.error("Failed to encrypt location coordinate", e);
            throw new RuntimeException("Encryption error", e);
        }
    }

    @Override
    public Double convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
            byte[] decoded = Base64.getDecoder().decode(dbData);

            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] ciphertext = new byte[byteBuffer.remaining()];
            byteBuffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] plaintext = cipher.doFinal(ciphertext);
            return Double.parseDouble(new String(plaintext, StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Failed to decrypt location coordinate", e);
            throw new RuntimeException("Decryption error", e);
        }
    }
}
