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
    public List<DestinationInsightDTO> getInsightsForDestination(String destination, String userEmail) {
        Instant cutoff = Instant.now().minus(java.time.Duration.ofDays(30));
        UUID userId = null;
        if (userEmail != null) {
            userId = userRepository.findByEmail(userEmail)
                    .map(com.travyn.auth.entity.User::getId)
                    .orElse(null);
        }
        UUID finalUserId = userId;
        return insightRepository.findActiveInsights(destination, cutoff)
                .stream()
                .map(insight -> mapToDTO(insight, finalUserId))
                .toList();
    }

    public DestinationInsightDTO postInsight(String userEmail, String destination, DestinationInsightRequest request) {
        com.travyn.auth.entity.User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!tripRepository.hasApprovedTripToDestination(destination, user.getId())) {
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
            List<com.travyn.auth.entity.User> activeUsers = tripRepository.findUsersCurrentlyInDestination(destination);
            for (com.travyn.auth.entity.User u : activeUsers) {
                if (!u.getId().equals(user.getId())) {
                    String message = "🚨 New traveler alert for your current destination (" + destination + ")";
                    try {
                        notificationService.notifyUser(u.getId(), message, NotificationType.DESTINATION_ALERT, insight.getId());
                    } catch (Exception ignored) {}
                }
            }
        }

        return mapToDTO(insight, user.getId());
    }

    public DestinationInsightDTO editInsight(String insightId, String userEmail, DestinationInsightRequest request) {
        com.travyn.auth.entity.User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
                
        DestinationInsight insight = insightRepository.findById(UUID.fromString(insightId))
                .orElseThrow(() -> new IllegalArgumentException("Insight not found"));
                
        if (!insight.getAuthorId().equals(user.getId())) {
            throw new IllegalArgumentException("You can only edit your own insights.");
        }
        
        insight.setCategory(request.getCategory());
        insight.setContent(request.getContent());
        insight = insightRepository.save(insight);
        
        return mapToDTO(insight, user.getId());
    }

    public void upvoteInsight(String insightId, String userEmail) {
        com.travyn.auth.entity.User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        DestinationInsight insight = insightRepository.findById(UUID.fromString(insightId))
                .orElseThrow(() -> new IllegalArgumentException("Insight not found"));
        
        if (insight.getUpvotedBy().contains(user.getId())) {
            insight.getUpvotedBy().remove(user.getId());
            insight.setUpvotes(insight.getUpvotedBy().size());
        } else {
            insight.getUpvotedBy().add(user.getId());
            insight.setUpvotes(insight.getUpvotedBy().size());
        }
        
        insightRepository.save(insight);
    }

    private DestinationInsightDTO mapToDTO(DestinationInsight insight, UUID currentUserId) {
        return DestinationInsightDTO.builder()
                .id(insight.getId())
                .destination(insight.getDestination())
                .authorId(insight.getAuthorId())
                .authorName(insight.getAuthorName())
                .authorAvatarUrl(insight.getAuthorAvatarUrl())
                .category(insight.getCategory())
                .content(insight.getContent())
                .upvotes(insight.getUpvotes())
                .isUpvotedByMe(currentUserId != null && insight.getUpvotedBy().contains(currentUserId))
                .createdAt(insight.getCreatedAt())
                .build();
    }
}
