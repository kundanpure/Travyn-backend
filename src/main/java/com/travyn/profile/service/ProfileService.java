package com.travyn.profile.service;

import com.travyn.auth.entity.Gender;
import com.travyn.auth.entity.User;
import com.travyn.auth.repository.UserRepository;
import com.travyn.common.exception.UserNotFoundException;
import com.travyn.profile.dto.ProfileDTO;
import com.travyn.profile.dto.UpdateProfileRequest;
import com.travyn.profile.entity.Profile;
import com.travyn.profile.entity.TravelStyle;
import com.travyn.profile.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final UserRepository userRepository;

    private static final int MAX_GENDER_CHANGES = 2;

    @Transactional(readOnly = true)
    public ProfileDTO getMyProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        Profile profile = profileRepository.findByUserId(userId)
                .orElseGet(() -> createEmptyProfile(user));

        return mapToDTO(profile, user);
    }

    @Transactional
    public ProfileDTO updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        Profile profile = profileRepository.findByUserId(userId)
                .orElseGet(() -> createEmptyProfile(user));

        if (request.getBio() != null) {
            profile.setBio(request.getBio().trim());
        }

        // Multi travel styles — validate each against the enum
        if (request.getTravelStyles() != null) {
            List<String> validStyles = request.getTravelStyles().stream()
                    .filter(s -> {
                        try { TravelStyle.valueOf(s); return true; } catch (Exception e) { return false; }
                    })
                    .distinct()
                    .collect(Collectors.toList());
            profile.setTravelStyles(validStyles.isEmpty() ? null : String.join(",", validStyles));
        }

        if (request.getBudgetMin() != null) {
            profile.setBudgetMin(request.getBudgetMin());
        }
        if (request.getBudgetMax() != null) {
            profile.setBudgetMax(request.getBudgetMax());
        }
        if (request.getSleepSchedule() != null) {
            profile.setSleepSchedule(request.getSleepSchedule());
        }
        if (request.getPersonalityScale() != null) {
            profile.setPersonalityScale(request.getPersonalityScale());
        }
        if (request.getFoodPreference() != null) {
            profile.setFoodPreference(request.getFoodPreference());
        }
        if (request.getLanguages() != null) {
            profile.setLanguages(request.getLanguages().trim());
        }
        if (request.getRemoteWorker() != null) {
            profile.setRemoteWorker(request.getRemoteWorker());
        }
        if (request.getProfilePhotoUrl() != null) {
            profile.setProfilePhotoUrl(request.getProfilePhotoUrl().trim());
        }
        if (request.getCoverPhotoUrl() != null) {
            profile.setCoverPhotoUrl(request.getCoverPhotoUrl().trim());
        }

        // Gender change — enforced max 2 times lifetime
        if (request.getGender() != null && request.getGender() != user.getGender()) {
            if (user.getGenderChangeCount() >= MAX_GENDER_CHANGES) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Gender can only be changed " + MAX_GENDER_CHANGES + " times. You have reached the limit.");
            }
            user.setGender(request.getGender());
            user.setGenderChangeCount(user.getGenderChangeCount() + 1);
            userRepository.save(user);
            log.info("User {} changed gender to {} (change #{}/{})",
                    userId, request.getGender(), user.getGenderChangeCount(), MAX_GENDER_CHANGES);
        }

        profile = profileRepository.save(profile);
        log.info("Profile updated for user: {}", userId);

        return mapToDTO(profile, user);
    }

    @Transactional(readOnly = true)
    public ProfileDTO getPublicProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        Profile profile = profileRepository.findByUserId(userId)
                .orElseGet(() -> createEmptyProfile(user));

        return mapToDTO(profile, user);
    }

    @Transactional
    protected Profile createEmptyProfile(User user) {
        Profile profile = Profile.builder()
                .user(user)
                .build();
        return profileRepository.save(profile);
    }

    public int calculateCompleteness(Profile profile, User user) {
        int totalFields = 11;
        int filledFields = 0;

        if (profile.getBio() != null && !profile.getBio().isBlank()) filledFields++;
        if (profile.getTravelStyles() != null && !profile.getTravelStyles().isBlank()) filledFields++;
        if (profile.getBudgetMin() != null) filledFields++;
        if (profile.getBudgetMax() != null) filledFields++;
        if (profile.getSleepSchedule() != null) filledFields++;
        if (profile.getPersonalityScale() != null) filledFields++;
        if (profile.getFoodPreference() != null) filledFields++;
        if (profile.getLanguages() != null && !profile.getLanguages().isBlank()) filledFields++;
        if (profile.getProfilePhotoUrl() != null && !profile.getProfilePhotoUrl().isBlank()) filledFields++;
        if (profile.getCoverPhotoUrl() != null && !profile.getCoverPhotoUrl().isBlank()) filledFields++;
        if (user.getGender() != null && user.getGender() != Gender.PREFER_NOT_TO_SAY) filledFields++;

        return (filledFields * 100) / totalFields;
    }

    private List<String> parseTravelStyles(String raw) {
        if (raw == null || raw.isBlank()) return Collections.emptyList();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private ProfileDTO mapToDTO(Profile profile, User user) {
        int changesRemaining = Math.max(0, MAX_GENDER_CHANGES - user.getGenderChangeCount());
        return ProfileDTO.builder()
                .userId(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .bio(profile.getBio())
                .travelStyles(parseTravelStyles(profile.getTravelStyles()))
                .budgetMin(profile.getBudgetMin())
                .budgetMax(profile.getBudgetMax())
                .sleepSchedule(profile.getSleepSchedule())
                .personalityScale(profile.getPersonalityScale())
                .foodPreference(profile.getFoodPreference())
                .languages(profile.getLanguages())
                .remoteWorker(profile.isRemoteWorker())
                .profilePhotoUrl(profile.getProfilePhotoUrl())
                .coverPhotoUrl(profile.getCoverPhotoUrl())
                .profileCompleteness(calculateCompleteness(profile, user))
                .build();
    }
}
