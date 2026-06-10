package com.travyn;

import com.travyn.safety.crypto.AesEncryptor;

public class TestDecrypt {
    public static void main(String[] args) {
        AesEncryptor encryptor = new AesEncryptor();
        encryptor.setKey("TrvynLoc2026AES256KeyMustBe32B!");
        
        String dbData = "hb9Dsqpdxj2E6LpnemPgAQywqh88pynqr15YK8iSnj3bJLnK6XtoafhORkqAhg==";
        try {
            Double result = encryptor.convertToEntityAttribute(dbData);
            System.out.println("Decrypted: " + result);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
