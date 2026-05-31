package com.travyn.kyc.controller;

import com.travyn.auth.entity.User;
import com.travyn.auth.repository.UserRepository;
import com.travyn.kyc.entity.KycRecord;
import com.travyn.kyc.service.AadhaarVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/kyc")
@RequiredArgsConstructor
public class KycController {

    private final AadhaarVerificationService aadhaarVerificationService;
    private final UserRepository userRepository;

    @PostMapping("/aadhaar/qr")
    public ResponseEntity<?> verifyAadhaarQr(
            @AuthenticationPrincipal String email,
            @RequestParam("image") MultipartFile image) {
        try {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));
                    
            KycRecord record = aadhaarVerificationService.verifyAadhaarQr(user.getId(), image);
            return ResponseEntity.ok(Map.of("message", "KYC Successful", "recordId", record.getId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/aadhaar/qr/raw")
    public ResponseEntity<?> verifyAadhaarQrRaw(
            @AuthenticationPrincipal String email,
            @RequestBody Map<String, String> payload) {
        try {
            String qrData = payload.get("qrData");
            if (qrData == null || qrData.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "qrData is required"));
            }
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));
                    
            KycRecord record = aadhaarVerificationService.verifyIdentityRaw(user.getId(), qrData);
            return ResponseEntity.ok(Map.of("message", "KYC Successful", "recordId", record.getId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
