package com.travyn.reputation.service;

import com.travyn.auth.entity.User;
import com.travyn.notification.entity.NotificationType;
import com.travyn.notification.service.NotificationService;
import com.travyn.reputation.entity.Review;
import com.travyn.reputation.repository.ReviewRepository;
import com.travyn.trip.entity.Trip;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled job that auto-publishes reviews after the 14-day review window closes.
 * 
 * Rule: If only one party reviewed within the window, that review is published
 * automatically once the window expires. Runs daily at 2:00 AM.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReviewAutoPublishScheduler {

    private final ReviewRepository reviewRepository;
    private final ReputationService reputationService;
    private final NotificationService notificationService;

    /** Must match the constants in ReputationService */
    private static final int WINDOW_OPEN_DELAY_DAYS = 1;
    private static final int WINDOW_DURATION_DAYS = 14;

    /**
     * Runs every day at 2:00 AM server time.
     * Finds all unpublished reviews whose trip's review window has closed,
     * publishes them, recomputes trust scores, and sends notifications.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void autoPublishExpiredReviews() {
        log.info("Running review auto-publish scheduler...");

        List<Review> unpublishedReviews = reviewRepository.findByIsPublishedFalse();

        int publishedCount = 0;

        for (Review review : unpublishedReviews) {
            Trip trip = review.getTrip();
            LocalDateTime windowCloses = trip.getEndDate()
                    .plusDays(WINDOW_OPEN_DELAY_DAYS)
                    .atStartOfDay()
                    .plusDays(WINDOW_DURATION_DAYS);

            if (LocalDateTime.now().isAfter(windowCloses)) {
                // Window has closed — auto-publish this review
                review.setIsPublished(true);
                reviewRepository.save(review);
                publishedCount++;

                // Recompute trust scores for both parties
                recomputeSafe(review.getReviewer().getId().toString());
                recomputeSafe(review.getReviewee().getId().toString());

                // Notify reviewer that their review was auto-published
                notificationService.notifyUser(
                        review.getReviewer().getId(),
                        "Your review for \"" + trip.getTitle() + "\" has been automatically published (14-day window expired).",
                        NotificationType.REVIEW_AUTO_PUBLISHED,
                        trip.getId()
                );

                // Notify reviewee that a review about them was auto-published
                notificationService.notifyUser(
                        review.getReviewee().getId(),
                        "A review about you from \"" + trip.getTitle() + "\" has been automatically published (14-day window expired).",
                        NotificationType.REVIEW_AUTO_PUBLISHED,
                        trip.getId()
                );

                log.info("Auto-published review {} for trip '{}' (reviewer={}, reviewee={})",
                        review.getId(), trip.getTitle(),
                        review.getReviewer().getId(), review.getReviewee().getId());
            }
        }

        log.info("Review auto-publish scheduler completed. Published {} reviews.", publishedCount);
    }

    private void recomputeSafe(String userId) {
        try {
            reputationService.recomputeTrustScore(userId);
        } catch (Exception e) {
            log.warn("Failed to recompute trust score for user {}: {}", userId, e.getMessage());
        }
    }
}
