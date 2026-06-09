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
import com.travyn.kyc.entity.KycRecord;
import com.travyn.kyc.entity.KycStatus;
import com.travyn.kyc.repository.KycRecordRepository;
import com.travyn.kyc.service.PreviewTokenService;
import com.travyn.profile.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

import java.time.Instant;
import java.util.Collections;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;
    private final ModelMapper modelMapper;
    private final PreviewTokenService previewTokenService;
    private final KycRecordRepository kycRecordRepository;
    private final ProfileRepository profileRepository;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    private GoogleIdTokenVerifier getGoogleVerifier() {
        return new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(googleClientId))
                .build();
    }

    public boolean isUsernameAvailable(String username) {
        if (username == null || username.isBlank() || username.length() < 3 || username.length() > 30) {
            return false;
        }
        return !userRepository.existsByUsernameIgnoreCase(username.trim());
    }

    @Transactional
    public AuthResponse googleLogin(GoogleAuthRequest request) {
        try {
            GoogleIdTokenVerifier verifier = getGoogleVerifier();
            GoogleIdToken idToken = verifier.verify(request.getCredential());
            if (idToken == null) {
                throw new InvalidCredentialsException("Invalid Google token");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail().toLowerCase().trim();

            return userRepository.findByEmail(email).map(user -> {
                // User exists, log them in
                if (user.getStatus() == UserStatus.BANNED) {
                    throw new AccountLockedException("This account has been suspended");
                }
                
                String accessToken = jwtUtil.generateAccessToken(user.getEmail(), user.getRole().name());
                String refreshTokenStr = jwtUtil.generateRefreshToken(user.getEmail());
                saveRefreshToken(user, refreshTokenStr);
                
                log.info("User logged in via Google: {}", user.getEmail());
                return AuthResponse.builder()
                        .accessToken(accessToken)
                        .refreshToken(refreshTokenStr)
                        .expiresIn(jwtUtil.getAccessTokenExpiryMs() / 1000)
                        .user(mapToUserDTO(user))
                        .build();
            }).orElseThrow(() -> {
                // User does not exist, throw exception to trigger Profile Completion
                String firstName = (String) payload.get("given_name");
                String lastName = (String) payload.get("family_name");
                String pictureUrl = (String) payload.get("picture");
                
                throw new GoogleProfileRequiredException(email, firstName, lastName, pictureUrl);
            });
            
        } catch (GoogleProfileRequiredException e) {
            throw e; // Rethrow to let the controller handle it
        } catch (Exception e) {
            log.error("Google login failed", e);
            throw new InvalidCredentialsException("Failed to verify Google token");
        }
    }

    @Transactional
    public AuthResponse googleRegister(GoogleRegisterRequest request) {
        try {
            GoogleIdTokenVerifier verifier = getGoogleVerifier();
            GoogleIdToken idToken = verifier.verify(request.getCredential());
            if (idToken == null) {
                throw new InvalidCredentialsException("Invalid Google token");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail().toLowerCase().trim();
            String pictureUrl = (String) payload.get("picture");

            if (userRepository.existsByEmail(email)) {
                throw new DuplicateEmailException("An account with this email already exists");
            }
            if (userRepository.existsByUsernameIgnoreCase(request.getUsername().trim())) {
                throw new IllegalArgumentException("Username is already taken");
            }

            User user = User.builder()
                    .email(email)
                    .username(request.getUsername().trim().toLowerCase())
                    .passwordHash(null) // No password
                    .firstName(request.getFirstName().trim())
                    .lastName(request.getLastName().trim())
                    .role(Role.REGISTERED)
                    .status(UserStatus.ACTIVE) // Auto-activated
                    .emailVerified(true) // Google verified
                    .authProvider(com.travyn.auth.entity.AuthProvider.GOOGLE)
                    .profilePictureUrl(pictureUrl)
                    .gender(request.getGender())
                    .dateOfBirth(request.getDateOfBirth())
                    .genderChangeCount(0)
                    .trustScore(10) // Small bump for Google Auth
                    .build();

            user = userRepository.save(user);
            log.info("New user registered via Google: {}", user.getEmail());

            String accessToken = jwtUtil.generateAccessToken(user.getEmail(), user.getRole().name());
            String refreshTokenStr = jwtUtil.generateRefreshToken(user.getEmail());
            saveRefreshToken(user, refreshTokenStr);

            return AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshTokenStr)
                    .expiresIn(jwtUtil.getAccessTokenExpiryMs() / 1000)
                    .user(mapToUserDTO(user))
                    .build();

        } catch (DuplicateEmailException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Google registration failed", e);
            throw new InvalidCredentialsException("Failed to verify Google token");
        }
    }

    @Transactional
    public AuthResponse aadhaarGoogleRegister(AadhaarGoogleRegisterRequest request) {
        // Validate Aadhaar token
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

        // Verify Google credential
        try {
            GoogleIdTokenVerifier verifier = getGoogleVerifier();
            GoogleIdToken idToken = verifier.verify(request.getCredential());
            if (idToken == null) {
                throw new InvalidCredentialsException("Invalid Google token");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail().toLowerCase().trim();
            String pictureUrl = (String) payload.get("picture");

            if (userRepository.existsByEmail(email)) {
                throw new DuplicateEmailException("An account with this email already exists");
            }
            if (userRepository.existsByUsernameIgnoreCase(request.getUsername().trim())) {
                throw new IllegalArgumentException("Username is already taken");
            }

            // Create user
            User user = User.builder()
                    .email(email)
                    .username(request.getUsername().trim().toLowerCase())
                    .passwordHash(null) // No password
                    .firstName(firstName)
                    .lastName(lastName)
                    .role(Role.VERIFIED)
                    .status(UserStatus.KYC_VERIFIED)
                    .emailVerified(true)
                    .authProvider(com.travyn.auth.entity.AuthProvider.GOOGLE)
                    .profilePictureUrl(pictureUrl)
                    .gender(gender)
                    .genderChangeCount(0)
                    .trustScore(50) // KYC trust bonus
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

            log.info("New user registered (Aadhaar + Google): {}", user.getEmail());

            String accessToken = jwtUtil.generateAccessToken(user.getEmail(), user.getRole().name());
            String refreshTokenStr = jwtUtil.generateRefreshToken(user.getEmail());
            saveRefreshToken(user, refreshTokenStr);

            return AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshTokenStr)
                    .expiresIn(jwtUtil.getAccessTokenExpiryMs() / 1000)
                    .user(mapToUserDTO(user))
                    .build();

        } catch (DuplicateEmailException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Google/Aadhaar registration failed", e);
            throw new InvalidCredentialsException("Failed to verify Google token");
        }
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

    public UserDTO getCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException());
        return mapToUserDTO(user);
    }

    @Transactional
    public void logout(String refreshTokenStr) {
        refreshTokenRepository.findByToken(refreshTokenStr).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
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

        String photoUrl = profileRepository.findByUserId(user.getId())
                .map(com.travyn.profile.entity.Profile::getProfilePhotoUrl)
                .orElse(null);

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
                .profilePhotoUrl(photoUrl)
                .build();
    }
}
