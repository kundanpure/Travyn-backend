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

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 15;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail().toLowerCase().trim())) {
            throw new DuplicateEmailException("An account with this email already exists");
        }

        String verificationToken = UUID.randomUUID().toString();

        Gender gender = request.getGender() != null ? request.getGender() : Gender.PREFER_NOT_TO_SAY;

        User user = User.builder()
                .email(request.getEmail().toLowerCase().trim())
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
        log.info("New user registered: {}", user.getEmail());

        // Send verification email
        emailService.sendVerificationEmail(user.getEmail(), user.getFirstName(), verificationToken);

        // Generate tokens so user can access limited features while verifying email
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
        return UserDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .status(user.getStatus())
                .emailVerified(user.isEmailVerified())
                .gender(user.getGender())
                .genderChangesRemaining(changesRemaining)
                .createdAt(user.getCreatedAt())
                .build();
    }
}
