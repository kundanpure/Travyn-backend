package com.travyn.profile.service;

import com.travyn.auth.entity.User;
import com.travyn.auth.repository.UserRepository;
import com.travyn.common.exception.UserNotFoundException;
import com.travyn.profile.dto.ProfileDTO;
import com.travyn.profile.dto.UpdateProfileRequest;
import com.travyn.profile.entity.Profile;
import com.travyn.profile.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final UserRepository userRepository;

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
        if (request.getTravelStyle() != null) {
            profile.setTravelStyle(request.getTravelStyle());
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

    public int calculateCompleteness(Profile profile) {
        int totalFields = 10;
        int filledFields = 0;

        if (profile.getBio() != null && !profile.getBio().isBlank()) filledFields++;
        if (profile.getTravelStyle() != null) filledFields++;
        if (profile.getBudgetMin() != null) filledFields++;
        if (profile.getBudgetMax() != null) filledFields++;
        if (profile.getSleepSchedule() != null) filledFields++;
        if (profile.getPersonalityScale() != null) filledFields++;
        if (profile.getFoodPreference() != null) filledFields++;
        if (profile.getLanguages() != null && !profile.getLanguages().isBlank()) filledFields++;
        if (profile.getProfilePhotoUrl() != null && !profile.getProfilePhotoUrl().isBlank()) filledFields++;
        if (profile.getCoverPhotoUrl() != null && !profile.getCoverPhotoUrl().isBlank()) filledFields++;

        return (filledFields * 100) / totalFields;
    }

    private ProfileDTO mapToDTO(Profile profile, User user) {
        return ProfileDTO.builder()
                .userId(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .bio(profile.getBio())
                .travelStyle(profile.getTravelStyle())
                .budgetMin(profile.getBudgetMin())
                .budgetMax(profile.getBudgetMax())
                .sleepSchedule(profile.getSleepSchedule())
                .personalityScale(profile.getPersonalityScale())
                .foodPreference(profile.getFoodPreference())
                .languages(profile.getLanguages())
                .remoteWorker(profile.isRemoteWorker())
                .profilePhotoUrl(profile.getProfilePhotoUrl())
                .coverPhotoUrl(profile.getCoverPhotoUrl())
                .profileCompleteness(calculateCompleteness(profile))
                .build();
    }
}
