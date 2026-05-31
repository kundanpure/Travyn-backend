package com.travyn.auth.controller;

import com.travyn.auth.dto.*;
import com.travyn.auth.service.AuthService;
import com.travyn.kyc.dto.AadhaarPreviewResponse;
import com.travyn.kyc.service.AadhaarVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
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
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
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
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/check-username")
    @Operation(summary = "Check if a username is available")
    public ResponseEntity<Map<String, Boolean>> checkUsername(@RequestParam("username") String username) {
        boolean isAvailable = authService.isUsernameAvailable(username);
        return ResponseEntity.ok(Map.of("available", isAvailable));
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(summary = "Login with email and password")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
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

    @GetMapping("/verify-email")
    @Operation(summary = "Verify email using token sent via email")
    public ResponseEntity<Map<String, String>> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(Map.of("message", "Email verified successfully"));
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Resend email verification link")
    public ResponseEntity<Map<String, String>> resendVerification(
            @Valid @RequestBody PasswordResetRequest request) {
        authService.resendVerificationEmail(request.getEmail());
        return ResponseEntity.ok(Map.of("message",
                "If your account exists and is unverified, a new verification email has been sent"));
    }

    @PostMapping("/password-reset/request")
    @Operation(summary = "Request a password reset link")
    public ResponseEntity<Map<String, String>> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request) {
        authService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok(Map.of("message",
                "If an account exists with this email, a password reset link has been sent"));
    }

    @PostMapping("/password-reset/confirm")
    @Operation(summary = "Reset password using token")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody PasswordResetConfirm request) {
        authService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Password has been reset successfully"));
    }
}
