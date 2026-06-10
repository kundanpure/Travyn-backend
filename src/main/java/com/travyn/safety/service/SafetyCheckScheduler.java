package com.travyn.safety.service;

import com.travyn.auth.entity.User;
import com.travyn.auth.repository.UserRepository;
import com.travyn.common.service.EmailService;
import com.travyn.notification.entity.NotificationType;
import com.travyn.notification.service.NotificationService;
import com.travyn.safety.entity.*;
import com.travyn.safety.repository.EmergencyContactRepository;
import com.travyn.safety.repository.SafetyCheckRepository;
import com.travyn.safety.repository.TripLocationSharingRepository;
import com.travyn.safety.repository.UserLocationHistoryRepository;
import com.travyn.trip.entity.TripWaypoint;
import com.travyn.trip.repository.TripWaypointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SafetyCheckScheduler {

    private final TripLocationSharingRepository sharingRepository;
    private final UserLocationHistoryRepository historyRepository;
    private final NotificationService notificationService;
    private final SafetyCheckRepository safetyCheckRepository;
    private final TripWaypointRepository tripWaypointRepository;
    private final EmergencyContactRepository emergencyContactRepository;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final com.travyn.safety.repository.SOSTokenRepository sosTokenRepository;
    private final com.travyn.common.service.TelegramBotService telegramBotService;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    private static final double EARTH_RADIUS_KM = 6371.0;

    @Scheduled(fixedRate = 300000) // Runs every 5 minutes
    public void checkImmobility() {
        log.info("Running safety geofence check...");
        List<TripLocationSharing> activeTrackers = sharingRepository.findByIsActiveTrue();
        Instant twoHoursAgo = Instant.now().minus(2, ChronoUnit.HOURS);

        for (TripLocationSharing tracker : activeTrackers) {
            List<UserLocationHistory> recentHistory = historyRepository.findRecentByUserIdAndTripId(
                    tracker.getUserId(), tracker.getTripId(), twoHoursAgo);

            if (recentHistory.size() < 2) continue; // Not enough data

            UserLocationHistory oldest = recentHistory.get(0);
            UserLocationHistory newest = recentHistory.get(recentHistory.size() - 1);

            double distanceMoved = calculateDistance(
                    oldest.getLatitude(), oldest.getLongitude(),
                    newest.getLatitude(), newest.getLongitude()
            ) * 1000; // in meters

            // If moved more than 10 meters in 2 hours, they are fine
            if (distanceMoved >= 10.0) continue;

            // Check if they are in a Safe Zone (within 200m of any waypoint for this trip)
            boolean inSafeZone = false;
            List<TripWaypoint> waypoints = tripWaypointRepository.findByTripIdOrderByCreatedAtAsc(tracker.getTripId());
            for (TripWaypoint wp : waypoints) {
                double dist = calculateDistance(newest.getLatitude(), newest.getLongitude(), wp.getLatitude(), wp.getLongitude()) * 1000;
                if (dist <= 200.0) {
                    inSafeZone = true;
                    break;
                }
            }
            
            // Also check accommodation if set
            if (!inSafeZone && tracker.getAccommodationLat() != null && tracker.getAccommodationLng() != null) {
                double dist = calculateDistance(newest.getLatitude(), newest.getLongitude(), tracker.getAccommodationLat(), tracker.getAccommodationLng()) * 1000;
                if (dist <= 200.0) {
                    inSafeZone = true;
                }
            }

            if (!inSafeZone) {
                // Check if a PENDING safety check already exists
                List<SafetyCheck> pendingChecks = safetyCheckRepository.findByUserIdAndStatus(tracker.getUserId(), SafetyCheckStatus.PENDING);
                if (!pendingChecks.isEmpty()) continue; // Already waiting for them to respond

                // Check if they recently clicked "I'm Safe" (RESOLVED within the last 1 hour)
                // This gives them a 1-hour "grace period" so we don't spam them immediately after they resolve it.
                Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
                List<SafetyCheck> resolvedChecks = safetyCheckRepository.findByUserIdAndStatus(tracker.getUserId(), SafetyCheckStatus.RESOLVED);
                boolean hasRecentResolvedCheck = resolvedChecks.stream()
                        .anyMatch(c -> c.getResolvedAt() != null && c.getResolvedAt().isAfter(oneHourAgo));
                
                if (hasRecentResolvedCheck) continue; // They are in their grace period

                log.warn("Safety alert: User {} stationary for >2 hours away from safe zones", tracker.getUserId());
                
                // Create Safety Check
                SafetyCheck safetyCheck = SafetyCheck.builder()
                        .userId(tracker.getUserId())
                        .tripId(tracker.getTripId())
                        .status(SafetyCheckStatus.PENDING)
                        .lastLat(newest.getLatitude())
                        .lastLng(newest.getLongitude())
                        .expiresAt(Instant.now().plus(15, ChronoUnit.MINUTES)) // 15 mins to respond
                        .build();
                safetyCheckRepository.save(safetyCheck);

                notificationService.notifyUser(
                        tracker.getUserId(),
                        "Safety Check: You haven't moved in 2 hours. Are you okay? Please tap 'I'm Safe'.",
                        NotificationType.SAFETY_CHECK,
                        tracker.getTripId()
                );
            }
        }
    }

    @Scheduled(fixedRate = 60000) // Runs every 1 minute
    public void escalateExpiredChecks() {
        List<SafetyCheck> expiredChecks = safetyCheckRepository.findByStatusAndExpiresAtBefore(SafetyCheckStatus.PENDING, Instant.now());
        
        for (SafetyCheck check : expiredChecks) {
            check.setStatus(SafetyCheckStatus.ESCALATED);
            check.setEscalatedAt(Instant.now());
            safetyCheckRepository.save(check);

            User user = userRepository.findById(check.getUserId()).orElse(null);
            if (user == null) continue;

            List<EmergencyContact> contacts = emergencyContactRepository.findByUserId(user.getId());
            if (contacts.isEmpty()) continue;

            log.error("🚨 ESCALATING SAFETY CHECK FOR USER: {}", user.getEmail());

            // Generate SOS Token
            byte[] randomBytes = new byte[32];
            new java.security.SecureRandom().nextBytes(randomBytes);
            String tokenStr = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

            SOSToken sosToken = SOSToken.builder()
                    .token(tokenStr)
                    .userId(user.getId())
                    .tripId(check.getTripId())
                    .safetyCheckId(check.getId())
                    .isActive(true)
                    .build();
            sosTokenRepository.save(sosToken);

            String trackingUrl = frontendUrl + "/share/sos/" + tokenStr;
            String travelerName = user.getFirstName() + " " + user.getLastName();

            for (EmergencyContact contact : contacts) {
                if (contact.getTelegramChatId() != null) {
                    telegramBotService.sendSOSAlert(
                            contact.getTelegramChatId(),
                            travelerName,
                            check.getLastLat(),
                            check.getLastLng(),
                            trackingUrl
                    );
                }
                
                emailService.sendSosEmail(contact.getEmail(), contact.getName(), travelerName, trackingUrl, check.getLastLat(), check.getLastLng());
            }
            
            notificationService.notifyUser(
                    user.getId(),
                    "Your safety check expired. We have notified your emergency contacts.",
                    NotificationType.SAFETY_CHECK,
                    check.getTripId()
            );
        }
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
}
