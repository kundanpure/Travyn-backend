package com.travyn.safety.controller;

import com.travyn.safety.dto.AccommodationPinRequest;
import com.travyn.safety.dto.LocationSharingStatusDTO;
import com.travyn.safety.dto.LocationUpdateRequest;
import com.travyn.safety.dto.ShareLinkDTO;
import com.travyn.safety.service.LocationSharingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Location Sharing", description = "Live location tracking and sharing")
public class LocationSharingController {

    private final LocationSharingService sharingService;
    private final com.travyn.auth.repository.UserRepository userRepository;

    private UUID getUserId(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"))
                .getId();
    }

    @GetMapping("/trips/{tripId}/location/status")
    @Operation(summary = "Get location sharing status for a trip")
    public ResponseEntity<LocationSharingStatusDTO> getStatus(
            @AuthenticationPrincipal String email,
            @PathVariable UUID tripId) {
        UUID userId = getUserId(email);
        return ResponseEntity.ok(sharingService.getStatus(userId, tripId));
    }

    @PostMapping("/trips/{tripId}/location/toggle")
    @Operation(summary = "Toggle location sharing on/off")
    public ResponseEntity<Void> toggleSharing(
            @AuthenticationPrincipal String email,
            @PathVariable UUID tripId,
            @RequestBody Map<String, Boolean> payload) {
        UUID userId = getUserId(email);
        sharingService.toggleSharing(userId, tripId, payload.getOrDefault("isActive", false));
        return ResponseEntity.ok().build();
    }

    @PutMapping("/trips/{tripId}/location/accommodation")
    @Operation(summary = "Set accommodation pin for safety checks")
    public ResponseEntity<Void> setAccommodationPin(
            @AuthenticationPrincipal String email,
            @PathVariable UUID tripId,
            @Valid @RequestBody AccommodationPinRequest request) {
        UUID userId = getUserId(email);
        sharingService.setAccommodationPin(userId, tripId, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/location")
    @Operation(summary = "Submit a GPS location update")
    public ResponseEntity<Void> recordLocation(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody LocationUpdateRequest request) {
        UUID userId = getUserId(email);
        sharingService.recordLocation(userId, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/trips/{tripId}/location/share")
    @Operation(summary = "Generate share links and notify emergency contacts")
    public ResponseEntity<List<ShareLinkDTO>> shareWithContacts(
            @AuthenticationPrincipal String email,
            @PathVariable UUID tripId) {
        UUID userId = getUserId(email);
        return ResponseEntity.ok(sharingService.generateAndShareLinks(userId, tripId));
    }
}
