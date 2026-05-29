package com.travyn.auth.service;

import com.travyn.auth.dto.*;
import com.travyn.auth.entity.Gender;
import com.travyn.auth.entity.RefreshToken;
import com.travyn.auth.entity.Role;
import com.travyn.auth.entity.User;
import com.travyn.auth.entity.UserStatus;
import com.travyn.auth.repository.RefreshTokenRepository;
import com.travyn.auth.repository.UserRepository;
import com.travyn.auth.security.JwtUtil;
import com.travyn.common.exception.*;
import com.travyn.common.service.EmailService;
import com.travyn.kyc.entity.KycRecord;
import com.travyn.kyc.entity.KycStatus;
import com.travyn.kyc.repository.KycRecordRepository;
import com.travyn.kyc.service.PreviewTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;
    private final ModelMapper modelMapper;
    private final PreviewTokenService previewTokenService;
    private final KycRecordRepository kycRecordRepository;

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 15;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail().toLowerCase().trim())) {
            throw new DuplicateEmailException("An account with this email already exists");
        }
        if (userRepository.existsByUsernameIgnoreCase(request.getUsername().trim())) {
            throw new IllegalArgumentException("Username is already taken");
        }

        // ── Aadhaar-first registration path ──────────────────────────────────────
        if (request.getPreviewToken() != null && !request.getPreviewToken().isBlank()) {
            return registerWithAadhaar(request);
        }

        // ── Email-first registration path (original) ─────────────────────────────
        if (request.getFirstName() == null || request.getFirstName().isBlank()) {
            throw new IllegalArgumentException("First name is required for email registration");
        }
        if (request.getLastName() == null || request.getLastName().isBlank()) {
            throw new IllegalArgumentException("Last name is required for email registration");
        }
        if (request.getGender() == null) {
            throw new IllegalArgumentException("Gender is required for email registration");
        }

        String verificationToken = UUID.randomUUID().toString();
        Gender gender = request.getGender();

        User user = User.builder()
                .email(request.getEmail().toLowerCase().trim())
                .username(request.getUsername().trim().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .role(Role.REGISTERED)
                .status(UserStatus.PENDING_VERIFICATION)
                .emailVerified(false)
                .emailVerificationToken(verificationToken)
                .gender(gender)
                .genderChangeCount(0)
                .build();

        user = userRepository.save(user);
        log.info("New user registered (email-first): {}", user.getEmail());

        emailService.sendVerificationEmail(user.getEmail(), user.getFirstName(), verificationToken);

        String accessToken = jwtUtil.generateAccessToken(user.getEmail(), user.getRole().name());
        String refreshTokenStr = jwtUtil.generateRefreshToken(user.getEmail());
        saveRefreshToken(user, refreshTokenStr);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenStr)
                .expiresIn(jwtUtil.getAccessTokenExpiryMs() / 1000)
                .user(mapToUserDTO(user))
                .build();
    }

    @Transactional
    private AuthResponse registerWithAadhaar(RegisterRequest request) {
        java.util.Map<String, Object> tokenData;
        try {
            tokenData = previewTokenService.validateAndDecode(request.getPreviewToken());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Aadhaar session expired or invalid. Please scan your QR again.");
        }

        String aadhaarName  = (String) tokenData.get("name");
        String aadhaarGender = (String) tokenData.get("gender");
        String aadhaarDob   = (String) tokenData.get("dob");
        String aadhaarLast4 = (String) tokenData.get("last4");

        // Split name into first/last
        String[] parts = aadhaarName.trim().split("\\s+");
        String firstName = parts.length == 1 ? parts[0] : aadhaarName.substring(0, aadhaarName.lastIndexOf(parts[parts.length - 1])).trim();
        String lastName  = parts.length == 1 ? "" : parts[parts.length - 1];

        Gender gender = Gender.PREFER_NOT_TO_SAY;
        try { gender = Gender.valueOf(aadhaarGender.toUpperCase()); } catch (Exception ignored) {}

        // Create user — KYC_VERIFIED immediately, email verified immediately
        User user = User.builder()
                .email(request.getEmail().toLowerCase().trim())
                .username(request.getUsername().trim().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(firstName)
                .lastName(lastName)
                .role(Role.VERIFIED)
                .status(UserStatus.KYC_VERIFIED)
                .emailVerified(true)   // Aadhaar is stronger proof
                .gender(gender)
                .genderChangeCount(0)
                .trustScore(50)        // KYC trust bonus
                .build();

        // Parse DOB
        try { user.setDateOfBirth(java.time.LocalDate.parse(aadhaarDob)); } catch (Exception ignored) {}

        user = userRepository.save(user);

        // Create KYC record
        KycRecord kycRecord = KycRecord.builder()
                .user(user)
                .aadhaarLast4(aadhaarLast4)
                .verifiedName(aadhaarName)
                .dob(aadhaarDob)
                .gender(aadhaarGender)
                .status(KycStatus.VERIFIED)
                .build();
        kycRecordRepository.save(kycRecord);

        log.info("New user registered (Aadhaar-first, KYC_VERIFIED): {}", user.getEmail());

        // Send welcome email (no verification needed)
        emailService.sendVerificationEmail(user.getEmail(), user.getFirstName(), null);

        String accessToken = jwtUtil.generateAccessToken(user.getEmail(), user.getRole().name());
        String refreshTokenStr = jwtUtil.generateRefreshToken(user.getEmail());
        saveRefreshToken(user, refreshTokenStr);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenStr)
                .expiresIn(jwtUtil.getAccessTokenExpiryMs() / 1000)
                .user(mapToUserDTO(user))
                .build();
    }

    public boolean isUsernameAvailable(String username) {
        if (username == null || username.isBlank() || username.length() < 3 || username.length() > 30) {
            return false;
        }
        return !userRepository.existsByUsernameIgnoreCase(username.trim());
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(InvalidCredentialsException::new);

        // Check lockout
        if (user.getLockoutUntil() != null && Instant.now().isBefore(user.getLockoutUntil())) {
            long minutesLeft = Instant.now().until(user.getLockoutUntil(), ChronoUnit.MINUTES) + 1;
            throw new AccountLockedException(
                    "Account is locked due to too many failed attempts. Try again in " + minutesLeft + " minutes");
        }

        // Validate password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            handleFailedLogin(user);
            throw new InvalidCredentialsException();
        }

        // Check email verification
        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException();
        }

        // Check account status
        if (user.getStatus() == UserStatus.BANNED) {
            throw new AccountLockedException("This account has been suspended");
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new AccountLockedException("This account is temporarily suspended");
        }

        // Reset failed attempts on successful login
        if (user.getFailedLoginAttempts() > 0) {
            user.setFailedLoginAttempts(0);
            user.setLockoutUntil(null);
            userRepository.save(user);
        }

        String accessToken = jwtUtil.generateAccessToken(user.getEmail(), user.getRole().name());
        String refreshTokenStr = jwtUtil.generateRefreshToken(user.getEmail());

        saveRefreshToken(user, refreshTokenStr);
        log.info("User logged in: {}", user.getEmail());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenStr)
                .expiresIn(jwtUtil.getAccessTokenExpiryMs() / 1000)
                .user(mapToUserDTO(user))
                .build();
    }

    @Transactional
    public AuthResponse refreshToken(String refreshTokenStr) {
        RefreshToken storedToken = refreshTokenRepository.findByToken(refreshTokenStr)
                .orElseThrow(() -> new TokenExpiredException("Invalid refresh token"));

        if (!storedToken.isUsable()) {
            // Potential token reuse attack — revoke all tokens for this user
            refreshTokenRepository.deleteByUser(storedToken.getUser());
            throw new TokenExpiredException("Refresh token has expired or been revoked");
        }

        // Rotate: revoke old, create new
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        User user = storedToken.getUser();
        String newAccessToken = jwtUtil.generateAccessToken(user.getEmail(), user.getRole().name());
        String newRefreshToken = jwtUtil.generateRefreshToken(user.getEmail());

        saveRefreshToken(user, newRefreshToken);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .expiresIn(jwtUtil.getAccessTokenExpiryMs() / 1000)
                .user(mapToUserDTO(user))
                .build();
    }

    @Transactional
    public void verifyEmail(String token) {
        User user = userRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new TokenExpiredException("Invalid or expired verification token"));

        user.setEmailVerified(true);
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerificationToken(null);
        userRepository.save(user);

        log.info("Email verified for user: {}", user.getEmail());
    }

    public UserDTO getCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException());
        return mapToUserDTO(user);
    }

    @Transactional
    public void resendVerificationEmail(String email) {
        User user = userRepository.findByEmail(email.toLowerCase().trim())
                .orElse(null);

        // Silently succeed even if user not found (prevent email enumeration)
        if (user == null) {
            log.debug("Resend verification requested for non-existent email: {}", email);
            return;
        }

        if (user.isEmailVerified()) {
            log.debug("Resend verification requested for already-verified user: {}", email);
            return;
        }

        // Generate fresh token and send
        String newToken = UUID.randomUUID().toString();
        user.setEmailVerificationToken(newToken);
        userRepository.save(user);

        emailService.sendVerificationEmail(user.getEmail(), user.getFirstName(), newToken);
        log.info("Verification email re-sent to: {}", email);
    }

    @Transactional
    public void requestPasswordReset(String email) {
        userRepository.findByEmail(email.toLowerCase().trim()).ifPresent(user -> {
            String resetToken = UUID.randomUUID().toString();
            user.setPasswordResetToken(resetToken);
            user.setPasswordResetExpiry(Instant.now().plus(1, ChronoUnit.HOURS));
            userRepository.save(user);
            log.info("Password reset requested for: {}", email);

            // Send password reset email
            emailService.sendPasswordResetEmail(user.getEmail(), user.getFirstName(), resetToken);
        });
        // Always return success to prevent email enumeration
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        User user = userRepository.findByPasswordResetToken(token)
                .orElseThrow(() -> new TokenExpiredException("Invalid or expired reset token"));

        if (user.getPasswordResetExpiry() == null || Instant.now().isAfter(user.getPasswordResetExpiry())) {
            throw new TokenExpiredException("Password reset token has expired");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordResetToken(null);
        user.setPasswordResetExpiry(null);
        user.setFailedLoginAttempts(0);
        user.setLockoutUntil(null);
        userRepository.save(user);

        // Revoke all refresh tokens for security
        refreshTokenRepository.deleteByUser(user);

        log.info("Password reset completed for: {}", user.getEmail());
    }

    @Transactional
    public void logout(String refreshTokenStr) {
        refreshTokenRepository.findByToken(refreshTokenStr).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    private void handleFailedLogin(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);

        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockoutUntil(Instant.now().plus(LOCKOUT_MINUTES, ChronoUnit.MINUTES));
            log.warn("Account locked for user: {} after {} failed attempts", user.getEmail(), attempts);
        }

        userRepository.save(user);
    }

    private void saveRefreshToken(User user, String tokenStr) {
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(tokenStr)
                .expiresAt(Instant.now().plusMillis(jwtUtil.getRefreshTokenExpiryMs()))
                .build();
        refreshTokenRepository.save(refreshToken);
    }

    private static final int MAX_GENDER_CHANGES = 2;

    private UserDTO mapToUserDTO(User user) {
        int changesRemaining = Math.max(0, MAX_GENDER_CHANGES - user.getGenderChangeCount());
        
        Integer age = null;
        String dob = null;
        if (user.getDateOfBirth() != null) {
            age = java.time.Period.between(user.getDateOfBirth(), java.time.LocalDate.now()).getYears();
            dob = user.getDateOfBirth().toString();
        }

        return UserDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .status(user.getStatus())
                .isVerified(user.getStatus() == UserStatus.KYC_VERIFIED)
                .emailVerified(user.isEmailVerified())
                .gender(user.getGender())
                .age(age)
                .dob(dob)
                .genderChangesRemaining(changesRemaining)
                .createdAt(user.getCreatedAt())
                .build();
    }
}
