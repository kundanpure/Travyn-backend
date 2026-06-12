package com.travyn.destination.service;

import com.travyn.auth.entity.User;
import com.travyn.auth.repository.UserRepository;
import com.travyn.destination.dto.DestinationInsightDTO;
import com.travyn.destination.dto.DestinationInsightRequest;
import com.travyn.destination.entity.DestinationInsight;
import com.travyn.destination.repository.DestinationInsightRepository;
import com.travyn.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.travyn.notification.service.NotificationService;
import com.travyn.notification.entity.NotificationType;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class DestinationInsightService {

    private final DestinationInsightRepository insightRepository;
    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<DestinationInsightDTO> getInsightsForDestination(String destination) {
        Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        return insightRepository.findActiveInsights(destination, cutoff)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public DestinationInsightDTO postInsight(String userEmail, String destination, DestinationInsightRequest request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Anti-spam verification
        boolean hasTraveled = tripRepository.hasApprovedTripToDestination(destination, user.getId());
        if (!hasTraveled) {
            throw new IllegalArgumentException("You can only post insights for destinations you have traveled to or are planning to travel to.");
        }

        DestinationInsight insight = DestinationInsight.builder()
                .destination(destination)
                .authorId(user.getId())
                .authorName(user.getFirstName() + " " + user.getLastName())
                .authorAvatarUrl(user.getProfilePictureUrl())
                .category(request.getCategory())
                .content(request.getContent())
                .build();

        insight = insightRepository.save(insight);
        
        // Trigger Proactive Notifications if this is an ALERT
        if (request.getCategory() == com.travyn.destination.entity.InsightCategory.ALERT) {
            List<User> activeUsers = tripRepository.findUsersCurrentlyInDestination(destination);
            for (User u : activeUsers) {
                if (!u.getId().equals(user.getId())) {
                    String message = "🚨 New traveler alert for your current destination (" + destination + ")";
                    notificationService.notifyUser(u.getId(), message, NotificationType.DESTINATION_ALERT, insight.getId());
                }
            }
        }
        
        return mapToDTO(insight);
    }

    public void upvoteInsight(String insightId) {
        DestinationInsight insight = insightRepository.findById(UUID.fromString(insightId))
                .orElseThrow(() -> new IllegalArgumentException("Insight not found"));
        insight.setUpvotes(insight.getUpvotes() + 1);
        insightRepository.save(insight);
    }

    private DestinationInsightDTO mapToDTO(DestinationInsight insight) {
        return DestinationInsightDTO.builder()
                .id(insight.getId())
                .destination(insight.getDestination())
                .authorId(insight.getAuthorId())
                .authorName(insight.getAuthorName())
                .authorAvatarUrl(insight.getAuthorAvatarUrl())
                .category(insight.getCategory())
                .content(insight.getContent())
                .upvotes(insight.getUpvotes())
                .createdAt(insight.getCreatedAt())
                .build();
    }
}
