package com.travyn.safety.service;

import com.travyn.notification.entity.NotificationType;
import com.travyn.notification.service.NotificationService;
import com.travyn.safety.entity.TripLocationSharing;
import com.travyn.safety.entity.UserLocationHistory;
import com.travyn.safety.repository.TripLocationSharingRepository;
import com.travyn.safety.repository.UserLocationHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SafetyCheckScheduler {

    private final TripLocationSharingRepository sharingRepository;
    private final UserLocationHistoryRepository historyRepository;
    private final NotificationService notificationService;

    private static final double EARTH_RADIUS_KM = 6371.0;

    @Scheduled(fixedRate = 300000) // Runs every 5 minutes
    public void checkImmobility() {
        log.info("Running safety geofence check...");
        List<TripLocationSharing> activeTrackers = sharingRepository.findByIsActiveTrue();
        Instant twoHoursAgo = Instant.now().minus(2, ChronoUnit.HOURS);

        for (TripLocationSharing tracker : activeTrackers) {
            if (tracker.getAccommodationLat() == null || tracker.getAccommodationLng() == null) {
                continue; // Can't verify if they are at accommodation
            }

            List<UserLocationHistory> recentHistory = historyRepository.findRecentByUserIdAndTripId(
                    tracker.getUserId(), tracker.getTripId(), twoHoursAgo);

            if (recentHistory.size() < 2) continue; // Not enough data

            UserLocationHistory oldest = recentHistory.get(0);
            UserLocationHistory newest = recentHistory.get(recentHistory.size() - 1);

            double distanceMoved = calculateDistance(
                    oldest.getLatitude(), oldest.getLongitude(),
                    newest.getLatitude(), newest.getLongitude()
            ) * 1000; // in meters

            double distanceFromAccommodation = calculateDistance(
                    newest.getLatitude(), newest.getLongitude(),
                    tracker.getAccommodationLat(), tracker.getAccommodationLng()
            ) * 1000;

            if (distanceMoved < 10.0 && distanceFromAccommodation > 100.0) {
                log.warn("Safety alert: User {} stationary for >2 hours away from accommodation", tracker.getUserId());
                notificationService.notifyUser(
                        tracker.getUserId(),
                        "Safety Check: You haven't moved in 2 hours. Are you okay? Please check-in.",
                        NotificationType.SAFETY_CHECK,
                        tracker.getTripId()
                );
            }
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
