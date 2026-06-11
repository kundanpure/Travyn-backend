package com.travyn.matching.service;

import com.travyn.auth.entity.User;
import com.travyn.auth.entity.UserStatus;
import com.travyn.auth.repository.UserRepository;
import com.travyn.common.exception.UserNotFoundException;
import com.travyn.matching.dto.CompatibilityBreakdownDTO;
import com.travyn.matching.dto.MatchCandidateDTO;
import com.travyn.matching.dto.MatchPreferencesDTO;
import com.travyn.matching.dto.SaveMatchPrefsRequest;
import com.travyn.matching.entity.MatchAction;
import com.travyn.matching.entity.MatchConnection;
import com.travyn.matching.entity.MatchPreferences;
import com.travyn.matching.entity.TravelMeaning;
import com.travyn.matching.entity.TravelMotivation;
import com.travyn.matching.repository.MatchConnectionRepository;
import com.travyn.matching.repository.MatchPreferencesRepository;
import com.travyn.profile.entity.Profile;
import com.travyn.profile.repository.ProfileRepository;
import com.travyn.profile.service.ProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.Period;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MatchingService {

    private final MatchPreferencesRepository prefsRepository;
    private final MatchConnectionRepository connectionRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final CompatibilityEngine compatibilityEngine;
    private final ProfileService profileService;

    @Transactional(readOnly = true)
    public MatchPreferencesDTO getPreferences(UUID userId) {
        return prefsRepository.findByUserId(userId)
                .map(this::mapToDTO)
                .orElse(null);
    }

    @Transactional
    public MatchPreferencesDTO savePreferences(UUID userId, SaveMatchPrefsRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        MatchPreferences prefs = prefsRepository.findByUserId(userId)
                .orElseGet(() -> MatchPreferences.builder().user(user).build());

        prefs.setSmokingHabit(request.getSmokingHabit());
        prefs.setDrinkingHabit(request.getDrinkingHabit());
        prefs.setTripPace(request.getTripPace());
        prefs.setAccommodationStyle(request.getAccommodationStyle());
        prefs.setPlanningStyle(request.getPlanningStyle());
        prefs.setCleanliness(request.getCleanliness());
        prefs.setSocialEnergy(request.getSocialEnergy());

        String motivations = request.getTravelMotivations().stream()
                .map(Enum::name)
                .collect(Collectors.joining(","));
        prefs.setTravelMotivations(motivations);

        String meanings = request.getTravelMeanings().stream()
                .map(Enum::name)
                .collect(Collectors.joining(","));
        prefs.setTravelMeanings(meanings);

        prefs.setTripExperience(request.getTripExperience());

        prefs = prefsRepository.save(prefs);
        return mapToDTO(prefs);
    }

    @Transactional(readOnly = true)
    public List<MatchCandidateDTO> getMatches(UUID userId) {
        User me = userRepository.findById(userId).orElseThrow();
        Profile myProfile = profileRepository.findByUserId(userId).orElse(null);
        MatchPreferences myPrefs = prefsRepository.findByUserId(userId).orElse(null);

        // If user hasn't completed quiz, return empty list
        if (myPrefs == null || myProfile == null) {
            return Collections.emptyList();
        }

        List<UUID> actionedIds = connectionRepository.findActionedTargetIds(userId);
        
        List<User> candidates = userRepository.findAll().stream()
                .filter(u -> !u.getId().equals(userId))
                .filter(u -> u.getStatus() == UserStatus.ACTIVE || u.getStatus() == UserStatus.KYC_VERIFIED)
                .filter(u -> !actionedIds.contains(u.getId()))
                .collect(Collectors.toList());

        List<MatchCandidateDTO> matches = new ArrayList<>();
        
        for (User candidate : candidates) {
            Profile targetProfile = profileRepository.findByUserId(candidate.getId()).orElse(null);
            MatchPreferences targetPrefs = prefsRepository.findByUserId(candidate.getId()).orElse(null);
            
            if (targetProfile == null || targetPrefs == null) continue;
            
            int completeness = profileService.calculateCompleteness(targetProfile, candidate);
            if (completeness < 0) continue; // Skip incomplete profiles

            CompatibilityBreakdownDTO breakdown = compatibilityEngine.score(myProfile, myPrefs, targetProfile, targetPrefs);
            
            MatchCandidateDTO dto = mapToCandidateDTO(candidate, myProfile, targetProfile, breakdown);
            matches.add(dto);
        }

        matches.sort((a, b) -> Integer.compare(b.getCompatibilityScore(), a.getCompatibilityScore()));
        return matches.stream().limit(20).collect(Collectors.toList());
    }

    @Transactional
    public void recordAction(UUID userId, UUID targetId, MatchAction action) {
        User me = userRepository.findById(userId).orElseThrow();
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target not found"));

        MatchConnection conn = connectionRepository.findByUserIdAndTargetId(userId, targetId).orElse(null);

        if (conn != null) {
            // Update existing connection (e.g. unblocking or accepting message request)
            conn.setAction(action);
            // Optionally update score if needed, but keeping old score is fine.
        } else {
            // Calculate score at the time of connection
            int score = 0;
            try {
                Profile myProfile = profileRepository.findByUserId(userId).orElse(null);
                MatchPreferences myPrefs = prefsRepository.findByUserId(userId).orElse(null);
                Profile targetProfile = profileRepository.findByUserId(targetId).orElse(null);
                MatchPreferences targetPrefs = prefsRepository.findByUserId(targetId).orElse(null);
                
                if (myProfile != null && targetProfile != null) {
                    score = compatibilityEngine.score(myProfile, myPrefs, targetProfile, targetPrefs).getOverallScore();
                }
            } catch (Exception ignored) {}

            conn = MatchConnection.builder()
                    .user(me)
                    .target(target)
                    .action(action)
                    .score(score)
                    .build();
        }
                
        connectionRepository.save(conn);
        log.info("User {} actioned {} on {}", userId, action, targetId);
    }

    @Transactional(readOnly = true)
    public List<MatchCandidateDTO> getMutualMatches(UUID userId) {
        List<UUID> mutualIds = connectionRepository.findMutualMatchUserIds(userId);
        
        if (mutualIds.isEmpty()) return Collections.emptyList();
        
        return mutualIds.stream()
                .map(id -> {
                    User user = userRepository.findById(id).orElse(null);
                    Profile profile = profileRepository.findByUserId(id).orElse(null);
                    if (user == null || profile == null) return null;
                    
                    // Score doesn't matter much here since they already matched, just pass dummy
                    CompatibilityBreakdownDTO dummy = CompatibilityBreakdownDTO.builder().overallScore(100).build();
                    Profile myProfile = profileRepository.findByUserId(userId).orElse(null);
                    return mapToCandidateDTO(user, myProfile, profile, dummy);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public CompatibilityBreakdownDTO getCompatibility(UUID userId, UUID targetId) {
        Profile myProfile = profileRepository.findByUserId(userId).orElseThrow();
        MatchPreferences myPrefs = prefsRepository.findByUserId(userId).orElseThrow();
        Profile targetProfile = profileRepository.findByUserId(targetId).orElseThrow();
        MatchPreferences targetPrefs = prefsRepository.findByUserId(targetId).orElseThrow();
        
        return compatibilityEngine.score(myProfile, myPrefs, targetProfile, targetPrefs);
    }

    private MatchCandidateDTO mapToCandidateDTO(User user, Profile myProfile, Profile targetProfile, CompatibilityBreakdownDTO breakdown) {
        Integer age = null;
        if (user.getDateOfBirth() != null) {
            age = Period.between(user.getDateOfBirth(), LocalDate.now()).getYears();
        }
        
        List<String> styles = new ArrayList<>();
        if (targetProfile.getTravelStyles() != null && !targetProfile.getTravelStyles().isEmpty()) {
            styles = Arrays.asList(targetProfile.getTravelStyles().split(","));
        }

        String personalityLabel = "Balanced";
        if (targetProfile.getPersonalityScale() != null) {
            if (targetProfile.getPersonalityScale() <= 3) personalityLabel = "Introvert";
            else if (targetProfile.getPersonalityScale() >= 8) personalityLabel = "Extrovert";
        }
        
        Double distanceInKm = null;
        if (myProfile != null && myProfile.getLatitude() != null && myProfile.getLongitude() != null &&
            targetProfile.getLatitude() != null && targetProfile.getLongitude() != null) {
            distanceInKm = calculateHaversineDistance(myProfile.getLatitude(), myProfile.getLongitude(),
                                                      targetProfile.getLatitude(), targetProfile.getLongitude());
        }

        return MatchCandidateDTO.builder()
                .userId(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .username(user.getUsername())
                .age(age)
                .profilePhotoUrl(targetProfile.getProfilePhotoUrl())
                .trustScore(user.getTrustScore())
                .travelStyles(styles)
                .budgetMin(targetProfile.getBudgetMin())
                .budgetMax(targetProfile.getBudgetMax())
                .personalityLabel(personalityLabel)
                .locationName(targetProfile.getLocationName())
                .distanceInKm(distanceInKm)
                .compatibilityScore(breakdown.getOverallScore())
                .breakdown(breakdown)
                .verified(user.getStatus() == UserStatus.KYC_VERIFIED)
                .build();
    }
    
    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Earth radius in km
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private MatchPreferencesDTO mapToDTO(MatchPreferences prefs) {
        List<TravelMotivation> motivations = new ArrayList<>();
        if (prefs.getTravelMotivations() != null && !prefs.getTravelMotivations().isEmpty()) {
            Arrays.stream(prefs.getTravelMotivations().split(","))
                  .forEach(s -> motivations.add(TravelMotivation.valueOf(s)));
        }
        
        List<TravelMeaning> meanings = new ArrayList<>();
        if (prefs.getTravelMeanings() != null && !prefs.getTravelMeanings().isEmpty()) {
            Arrays.stream(prefs.getTravelMeanings().split(","))
                  .forEach(s -> meanings.add(TravelMeaning.valueOf(s)));
        }
        
        return MatchPreferencesDTO.builder()
                .userId(prefs.getUser().getId())
                .smokingHabit(prefs.getSmokingHabit())
                .drinkingHabit(prefs.getDrinkingHabit())
                .tripPace(prefs.getTripPace())
                .accommodationStyle(prefs.getAccommodationStyle())
                .planningStyle(prefs.getPlanningStyle())
                .cleanliness(prefs.getCleanliness())
                .socialEnergy(prefs.getSocialEnergy())
                .travelMotivations(motivations)
                .travelMeanings(meanings)
                .tripExperience(prefs.getTripExperience())
                .build();
    }
}
