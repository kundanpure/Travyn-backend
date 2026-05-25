package com.travyn.profile.controller;

import com.travyn.auth.entity.User;
import com.travyn.auth.repository.UserRepository;
import com.travyn.common.exception.UserNotFoundException;
import com.travyn.profile.dto.ProfileDTO;
import com.travyn.profile.dto.UpdateProfileRequest;
import com.travyn.profile.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Profile", description = "User profile management")
public class ProfileController {

    private final ProfileService profileService;
    private final UserRepository userRepository;

    @GetMapping("/me/profile")
    @Operation(summary = "Get current user's profile")
    public ResponseEntity<ProfileDTO> getMyProfile(@AuthenticationPrincipal String email) {
        User user = findUserByEmail(email);
        ProfileDTO profile = profileService.getMyProfile(user.getId());
        return ResponseEntity.ok(profile);
    }

    @PutMapping("/me/profile")
    @Operation(summary = "Update current user's profile")
    public ResponseEntity<ProfileDTO> updateMyProfile(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody UpdateProfileRequest request) {
        User user = findUserByEmail(email);
        ProfileDTO profile = profileService.updateProfile(user.getId(), request);
        return ResponseEntity.ok(profile);
    }

    @GetMapping("/{userId}/profile")
    @Operation(summary = "Get a user's public profile")
    public ResponseEntity<ProfileDTO> getPublicProfile(@PathVariable UUID userId) {
        ProfileDTO profile = profileService.getPublicProfile(userId);
        return ResponseEntity.ok(profile);
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }
}
