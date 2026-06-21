package com.travyn.auth.controller;

import com.travyn.auth.dto.*;
import com.travyn.auth.service.AuthService;
import com.travyn.kyc.dto.AadhaarPreviewResponse;
import com.travyn.kyc.service.AadhaarVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.travyn.common.exception.GoogleProfileRequiredException;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Registration, login, token management")
public class AuthController {

    private final AuthService authService;
    private final AadhaarVerificationService aadhaarVerificationService;

    @PostMapping("/aadhaar-preview")
    @Operation(summary = "Parse Aadhaar QR for preview (no DB save) to generate registration token")
    public ResponseEntity<?> previewAadhaar(@RequestParam("image") MultipartFile image) {
        try {
            AadhaarPreviewResponse response = aadhaarVerificationService.previewAadhaarQr(image);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Aadhaar preview failed", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Unknown error occurred"));
        }
    }

    @PostMapping("/aadhaar-preview/raw")
    @Operation(summary = "Parse Aadhaar QR string for preview (no DB save) to generate registration token")
    public ResponseEntity<?> previewAadhaarRaw(@RequestBody Map<String, String> payload) {
        try {
            String qrData = payload.get("qrData");
            if (qrData == null || qrData.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "qrData is required"));
            }
            AadhaarPreviewResponse response = aadhaarVerificationService.decodeRawAndPreview(qrData);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Aadhaar raw preview failed", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Unknown error occurred"));
        }
    }

    @GetMapping("/check-username")
    @Operation(summary = "Check if a username is available")
    public ResponseEntity<Map<String, Boolean>> checkUsername(@RequestParam("username") String username) {
        boolean isAvailable = authService.isUsernameAvailable(username);
        return ResponseEntity.ok(Map.of("available", isAvailable));
    }

    @PostMapping("/aadhaar/google-register")
    @Operation(summary = "Register using Aadhaar + Google account binding")
    public ResponseEntity<AuthResponse> aadhaarGoogleRegister(@Valid @RequestBody AadhaarGoogleRegisterRequest request) {
        AuthResponse response = authService.aadhaarGoogleRegister(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/google/login")
    @Operation(summary = "Login with Google credential token")
    public ResponseEntity<?> googleLogin(@Valid @RequestBody GoogleAuthRequest request) {
        try {
            AuthResponse response = authService.googleLogin(request);
            return ResponseEntity.ok(response);
        } catch (GoogleProfileRequiredException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "PROFILE_COMPLETION_REQUIRED");
            errorResponse.put("message", "Please complete your profile to continue");
            errorResponse.put("email", e.getEmail());
            errorResponse.put("firstName", e.getFirstName());
            errorResponse.put("lastName", e.getLastName());
            errorResponse.put("profilePictureUrl", e.getProfilePictureUrl());
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(errorResponse);
        }
    }

    @PostMapping("/google/register")
    @Operation(summary = "Complete registration after Google login")
    public ResponseEntity<AuthResponse> googleRegister(@Valid @RequestBody GoogleRegisterRequest request) {
        AuthResponse response = authService.googleRegister(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user")
    public ResponseEntity<UserDTO> getCurrentUser(@org.springframework.security.core.annotation.AuthenticationPrincipal String email) {
        UserDTO userDTO = authService.getCurrentUser(email);
        return ResponseEntity.ok(userDTO);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token using refresh token")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request.getRefreshToken());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout and revoke refresh token")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }
}
