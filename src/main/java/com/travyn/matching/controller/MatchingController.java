package com.travyn.matching.controller;

import com.travyn.auth.entity.User;
import com.travyn.auth.repository.UserRepository;
import com.travyn.common.exception.UserNotFoundException;
import com.travyn.matching.dto.CompatibilityBreakdownDTO;
import com.travyn.matching.dto.MatchCandidateDTO;
import com.travyn.matching.dto.MatchPreferencesDTO;
import com.travyn.matching.dto.SaveMatchPrefsRequest;
import com.travyn.matching.entity.MatchAction;
import com.travyn.matching.service.MatchingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/matches")
@RequiredArgsConstructor
public class MatchingController {

    private final MatchingService matchingService;
    private final UserRepository userRepository;

    @GetMapping("/preferences")
    public ResponseEntity<MatchPreferencesDTO> getMyPreferences(@AuthenticationPrincipal String email) {
        UUID userId = findUserByEmail(email).getId();
        MatchPreferencesDTO prefs = matchingService.getPreferences(userId);
        if (prefs == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(prefs);
    }

    @PostMapping("/preferences")
    public ResponseEntity<MatchPreferencesDTO> savePreferences(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody SaveMatchPrefsRequest request) {
        UUID userId = findUserByEmail(email).getId();
        return ResponseEntity.ok(matchingService.savePreferences(userId, request));
    }

    @PutMapping("/preferences")
    public ResponseEntity<MatchPreferencesDTO> updatePreferences(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody SaveMatchPrefsRequest request) {
        UUID userId = findUserByEmail(email).getId();
        return ResponseEntity.ok(matchingService.savePreferences(userId, request));
    }

    @GetMapping
    public ResponseEntity<List<MatchCandidateDTO>> getMatches(@AuthenticationPrincipal String email) {
        UUID userId = findUserByEmail(email).getId();
        return ResponseEntity.ok(matchingService.getMatches(userId));
    }

    @GetMapping("/mutual")
    public ResponseEntity<List<MatchCandidateDTO>> getMutualMatches(@AuthenticationPrincipal String email) {
        UUID userId = findUserByEmail(email).getId();
        return ResponseEntity.ok(matchingService.getMutualMatches(userId));
    }

    @GetMapping("/{targetId}/compatibility")
    public ResponseEntity<CompatibilityBreakdownDTO> getCompatibility(
            @AuthenticationPrincipal String email,
            @PathVariable UUID targetId) {
        UUID userId = findUserByEmail(email).getId();
        return ResponseEntity.ok(matchingService.getCompatibility(userId, targetId));
    }

    @PostMapping("/{targetId}/connect")
    public ResponseEntity<Void> connect(
            @AuthenticationPrincipal String email,
            @PathVariable UUID targetId) {
        UUID userId = findUserByEmail(email).getId();
        matchingService.recordAction(userId, targetId, MatchAction.CONNECT);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{targetId}/pass")
    public ResponseEntity<Void> pass(
            @AuthenticationPrincipal String email,
            @PathVariable UUID targetId) {
        UUID userId = findUserByEmail(email).getId();
        matchingService.recordAction(userId, targetId, MatchAction.PASS);
        return ResponseEntity.ok().build();
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }
}
