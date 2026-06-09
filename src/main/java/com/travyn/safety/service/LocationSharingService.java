package com.travyn.safety.service;

import com.travyn.common.exception.ResourceNotFoundException;
import com.travyn.common.service.EmailService;
import com.travyn.safety.dto.*;
import com.travyn.safety.entity.EmergencyContact;
import com.travyn.safety.entity.LocationShareLink;
import com.travyn.safety.entity.TripLocationSharing;
import com.travyn.safety.entity.UserLocationHistory;
import com.travyn.safety.repository.EmergencyContactRepository;
import com.travyn.safety.repository.LocationShareLinkRepository;
import com.travyn.safety.repository.TripLocationSharingRepository;
import com.travyn.safety.repository.UserLocationHistoryRepository;
import com.travyn.trip.entity.Trip;
import com.travyn.trip.repository.TripRepository;
import com.travyn.auth.entity.User;
import com.travyn.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocationSharingService {

    private final TripLocationSharingRepository sharingRepository;
    private final UserLocationHistoryRepository historyRepository;
    private final LocationShareLinkRepository linkRepository;
    private final EmergencyContactRepository contactRepository;
    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public void toggleSharing(UUID userId, UUID tripId, boolean isActive) {
        TripLocationSharing sharing = sharingRepository.findByUserIdAndTripId(userId, tripId)
                .orElse(TripLocationSharing.builder()
                        .userId(userId)
                        .tripId(tripId)
                        .build());
        sharing.setActive(isActive);
        sharingRepository.save(sharing);
    }

    @Transactional
    public void setAccommodationPin(UUID userId, UUID tripId, AccommodationPinRequest request) {
        TripLocationSharing sharing = sharingRepository.findByUserIdAndTripId(userId, tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Location sharing not configured for this trip"));
        sharing.setAccommodationLat(request.getLatitude());
        sharing.setAccommodationLng(request.getLongitude());
        sharing.setAccommodationLabel(request.getLabel());
        sharingRepository.save(sharing);
    }

    @Transactional(readOnly = true)
    public LocationSharingStatusDTO getStatus(UUID userId, UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));

        TripLocationSharing sharing = sharingRepository.findByUserIdAndTripId(userId, tripId)
                .orElse(null);

        UserLocationHistory lastLocation = historyRepository.findLatestByUserIdAndTripId(userId, tripId)
                .orElse(null);

        return LocationSharingStatusDTO.builder()
                .tripId(tripId)
                .tripTitle(trip.getTitle())
                .isActive(sharing != null && sharing.isActive())
                .accommodationLat(sharing != null ? sharing.getAccommodationLat() : null)
                .accommodationLng(sharing != null ? sharing.getAccommodationLng() : null)
                .accommodationLabel(sharing != null ? sharing.getAccommodationLabel() : null)
                .lastLatitude(lastLocation != null ? lastLocation.getLatitude() : null)
                .lastLongitude(lastLocation != null ? lastLocation.getLongitude() : null)
                .lastRecordedAt(lastLocation != null ? lastLocation.getRecordedAt().toString() : null)
                .build();
    }

    @Transactional
    public void recordLocation(UUID userId, LocationUpdateRequest request) {
        if (request.getAccuracy() > 20000) {
            log.warn("Discarding location update due to low accuracy: {}m", request.getAccuracy());
            return;
        }

        TripLocationSharing sharing = sharingRepository.findByUserIdAndTripId(userId, request.getTripId())
                .orElse(null);
        if (sharing == null || !sharing.isActive()) {
            return; // Sharing is off
        }

        UserLocationHistory history = UserLocationHistory.builder()
                .userId(userId)
                .tripId(request.getTripId())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .accuracy(request.getAccuracy())
                .build();
        historyRepository.save(history);
    }

    @Transactional
    public List<ShareLinkDTO> generateAndShareLinks(UUID userId, UUID tripId) {
        User traveler = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));

        List<EmergencyContact> contacts = contactRepository.findByUserId(userId);
        if (contacts.isEmpty()) {
            throw new IllegalStateException("No emergency contacts configured");
        }

        // Make sure sharing is active
        toggleSharing(userId, tripId, true);

        return contacts.stream().map(contact -> {
            String token = UUID.randomUUID().toString();
            LocationShareLink link = LocationShareLink.builder()
                    .token(token)
                    .userId(userId)
                    .tripId(tripId)
                    .emergencyContactId(contact.getId())
                    .expiresAt(Instant.now().plus(72, ChronoUnit.HOURS))
                    .build();
            linkRepository.save(link);

            String url = "http://localhost:3000/share/location/" + token;
            // emailService.sendShareEmail(traveler, contact, trip, url); // Removed: Now relying on UI to show shareable link

            return ShareLinkDTO.builder()
                    .contactName(contact.getName())
                    .contactEmail(contact.getEmail())
                    .shareUrl(url)
                    .expiresAt(link.getExpiresAt().toString())
                    .build();
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PublicLocationDTO getPublicLocation(String token) {
        LocationShareLink link = linkRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid or expired link"));

        if (Instant.now().isAfter(link.getExpiresAt())) {
            return PublicLocationDTO.builder().expired(true).message("This tracking link has expired.").build();
        }

        TripLocationSharing sharing = sharingRepository.findByUserIdAndTripId(link.getUserId(), link.getTripId())
                .orElse(null);
        if (sharing == null || !sharing.isActive()) {
            return PublicLocationDTO.builder().expired(true).message("The traveler has paused their location sharing.").build();
        }

        User traveler = userRepository.findById(link.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Traveler not found"));
        Trip trip = tripRepository.findById(link.getTripId())
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));

        UserLocationHistory lastLocation = historyRepository.findLatestByUserIdAndTripId(traveler.getId(), trip.getId())
                .orElse(null);

        return PublicLocationDTO.builder()
                .travelerName(traveler.getFirstName())
                .latitude(lastLocation != null ? lastLocation.getLatitude() : null)
                .longitude(lastLocation != null ? lastLocation.getLongitude() : null)
                .lastUpdated(lastLocation != null ? lastLocation.getRecordedAt().toString() : null)
                .tripTitle(trip.getTitle())
                .tripDestination(trip.getDestination())
                .expired(false)
                .build();
    }

    private void sendShareEmail(User traveler, EmergencyContact contact, Trip trip, String link) {
        emailService.sendLocationShareEmail(
                contact.getEmail(),
                contact.getName(),
                traveler.getFirstName(),
                trip.getDestination(),
                link
        );
    }
}
