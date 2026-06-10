package com.travyn.safety.controller;

import com.travyn.auth.entity.User;
import com.travyn.auth.repository.UserRepository;
import com.travyn.common.exception.ResourceNotFoundException;
import com.travyn.safety.dto.PublicSOSDTO;
import com.travyn.safety.entity.SOSToken;
import com.travyn.safety.repository.SOSTokenRepository;
import com.travyn.safety.repository.UserLocationHistoryRepository;
import com.travyn.safety.entity.UserLocationHistory;
import com.travyn.trip.entity.Trip;
import com.travyn.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/public/sos")
@RequiredArgsConstructor
public class PublicSOSController {

    private final SOSTokenRepository sosTokenRepository;
    private final UserRepository userRepository;
    private final TripRepository tripRepository;
    private final UserLocationHistoryRepository locationHistoryRepository;

    @GetMapping("/{token}")
    public ResponseEntity<PublicSOSDTO> getSOSDetails(@PathVariable String token) {
        SOSToken sosToken = sosTokenRepository.findByTokenAndIsActiveTrue(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid or inactive SOS token"));

        User user = userRepository.findById(sosToken.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Trip trip = null;
        if (sosToken.getTripId() != null) {
            trip = tripRepository.findById(sosToken.getTripId()).orElse(null);
        }

        Optional<UserLocationHistory> lastLocation = locationHistoryRepository
                .findFirstByUserIdOrderByRecordedAtDesc(user.getId());

        PublicSOSDTO dto = PublicSOSDTO.builder()
                .userName(user.getFirstName() + " " + user.getLastName())
                .tripName(trip != null ? trip.getTitle() : "Unknown Trip")
                .tripDestination(trip != null ? trip.getDestination() : "Unknown Destination")
                .sosTriggeredAt(sosToken.getCreatedAt())
                .isActive(sosToken.isActive())
                .build();

        if (lastLocation.isPresent()) {
            dto.setLastLat(lastLocation.get().getLatitude());
            dto.setLastLng(lastLocation.get().getLongitude());
            dto.setLastLocationTime(lastLocation.get().getRecordedAt());
        }

        return ResponseEntity.ok(dto);
    }
}
