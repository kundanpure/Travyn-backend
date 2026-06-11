package com.travyn.reputation.service;

import com.travyn.auth.entity.User;
import com.travyn.auth.repository.UserRepository;
import com.travyn.notification.entity.NotificationType;
import com.travyn.notification.service.NotificationService;
import com.travyn.profile.repository.ProfileRepository;
import com.travyn.reputation.dto.ReviewDTO;
import com.travyn.reputation.dto.ReviewRequest;
import com.travyn.reputation.dto.ReviewWindowDTO;
import com.travyn.reputation.dto.TrustScoreDTO;
import com.travyn.reputation.entity.Review;
import com.travyn.reputation.entity.TrustScore;
import com.travyn.reputation.repository.ReviewRepository;
import com.travyn.reputation.repository.TrustScoreRepository;
import com.travyn.trip.entity.Trip;
import com.travyn.trip.entity.MemberStatus;
import com.travyn.trip.entity.TripMember;
import com.travyn.trip.repository.TripMemberRepository;
import com.travyn.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReputationService {

    private final ReviewRepository reviewRepository;
    private final TrustScoreRepository trustScoreRepository;
    private final UserRepository userRepository;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final NotificationService notificationService;
    private final ProfileRepository profileRepository;
    private final ModelMapper modelMapper;

    /** Review window opens 24 hours after trip end date, closes 14 days later */
    private static final int WINDOW_OPEN_DELAY_DAYS = 1;
    private static final int WINDOW_DURATION_DAYS = 14;

    @Transactional
    public ReviewDTO submitReview(String reviewerEmail, String tripId, String revieweeId, ReviewRequest request) {
        User reviewer = userRepository.findByEmail(reviewerEmail)
                .orElseThrow(() -> new RuntimeException("Reviewer not found"));
        User reviewee = userRepository.findById(UUID.fromString(revieweeId))
                .orElseThrow(() -> new RuntimeException("Reviewee not found"));
        
        UUID tId = UUID.fromString(tripId);
        Trip trip = tripRepository.findById(tId)
                .orElseThrow(() -> new RuntimeException("Trip not found"));

        // Check if both users were APPROVED members of this trip
        TripMember m1 = tripMemberRepository.findByTripIdAndUserId(tId, reviewer.getId())
                .orElseThrow(() -> new RuntimeException("You are not a member of this trip"));
        TripMember m2 = tripMemberRepository.findByTripIdAndUserId(tId, UUID.fromString(revieweeId))
                .orElseThrow(() -> new RuntimeException("Reviewee was not a member of this trip"));

        if (m1.getMemberStatus() != MemberStatus.APPROVED || m2.getMemberStatus() != MemberStatus.APPROVED) {
            throw new RuntimeException("Both users must be approved members of the trip to review");
        }

        // ── Review Window Enforcement ──
        LocalDateTime windowOpens = trip.getEndDate().plusDays(WINDOW_OPEN_DELAY_DAYS).atStartOfDay();
        LocalDateTime windowCloses = windowOpens.plusDays(WINDOW_DURATION_DAYS);
        LocalDateTime now = LocalDateTime.now();

        if (now.isBefore(windowOpens)) {
            throw new RuntimeException("Review window hasn't opened yet — it opens 24 hours after the trip ends.");
        }
        if (now.isAfter(windowCloses)) {
            throw new RuntimeException("Review window has closed. Reviews were accepted until " + windowCloses.toLocalDate() + ".");
        }

        // Check if a review already exists
        if (reviewRepository.findByTripIdAndReviewerIdAndRevieweeId(tId, reviewer.getId(), UUID.fromString(revieweeId)).isPresent()) {
            throw new RuntimeException("You have already reviewed this user for this trip");
        }

        Review review = new Review();
        review.setTrip(trip);
        review.setReviewer(reviewer);
        review.setReviewee(reviewee);
        review.setPunctualityRating(request.getPunctualityRating());
        review.setCleanlinessRating(request.getCleanlinessRating());
        review.setCommunicationRating(request.getCommunicationRating());
        review.setVibeRating(request.getVibeRating());
        review.setSafetyRating(request.getSafetyRating());
        review.setTextReview(request.getTextReview());
        review.setCreatedAt(LocalDateTime.now());
        review.setIsPublished(false); // Default false for mutual review logic

        reviewRepository.save(review);

        // ── Notify the reviewee that they were reviewed ──
        notificationService.notifyUser(
                reviewee.getId(),
                reviewer.getFirstName() + " has reviewed you from trip \"" + trip.getTitle() + "\". Review them back!",
                NotificationType.REVIEW_RECEIVED,
                tId
        );

        // Check if the mutual review exists
        Optional<Review> mutualReview = reviewRepository.findByTripIdAndReviewerIdAndRevieweeId(tId, UUID.fromString(revieweeId), reviewer.getId());
        if (mutualReview.isPresent()) {
            // Both sides have reviewed! Publish both.
            review.setIsPublished(true);
            Review reciprocal = mutualReview.get();
            reciprocal.setIsPublished(true);
            reviewRepository.save(review);
            reviewRepository.save(reciprocal);
            
            // Recompute TrustScores since new reviews are published
            recomputeTrustScore(reviewer.getId().toString());
            recomputeTrustScore(revieweeId);

            // Notify both that mutual reviews are now published
            notificationService.notifyUser(
                    reviewer.getId(),
                    "Your mutual reviews for \"" + trip.getTitle() + "\" are now published! 🎉",
                    NotificationType.REVIEWS_PUBLISHED,
                    tId
            );
            notificationService.notifyUser(
                    reviewee.getId(),
                    "Your mutual reviews for \"" + trip.getTitle() + "\" are now published! 🎉",
                    NotificationType.REVIEWS_PUBLISHED,
                    tId
            );
        }

        return convertToDto(review);
    }

    /**
     * Returns the review window status for a given trip and user.
     * Shows window open/close times, and per-peer review status.
     */
    @Transactional(readOnly = true)
    public ReviewWindowDTO getReviewWindowStatus(String tripId, String userEmail) {
        UUID tId = UUID.fromString(tripId);
        Trip trip = tripRepository.findById(tId)
                .orElseThrow(() -> new RuntimeException("Trip not found"));

        User currentUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        LocalDateTime windowOpens = trip.getEndDate().plusDays(WINDOW_OPEN_DELAY_DAYS).atStartOfDay();
        LocalDateTime windowCloses = windowOpens.plusDays(WINDOW_DURATION_DAYS);
        LocalDateTime now = LocalDateTime.now();
        boolean windowOpen = !now.isBefore(windowOpens) && !now.isAfter(windowCloses);

        // Get all approved members of the trip (excluding current user)
        List<TripMember> members = tripMemberRepository.findByTripIdAndMemberStatus(tId, MemberStatus.APPROVED);

        // Get all reviews for this trip
        List<Review> allReviews = reviewRepository.findByTripId(tId);

        List<ReviewWindowDTO.PeerReviewStatus> peerStatuses = members.stream()
                .filter(m -> !m.getUserId().equals(currentUser.getId()))
                .map(m -> {
                    User peerUser = userRepository.findById(m.getUserId()).orElse(null);
                    String peerName = peerUser != null
                            ? peerUser.getFirstName() + " " + peerUser.getLastName()
                            : "Unknown";
                    String photoUrl = peerUser != null
                            ? profileRepository.findByUserId(peerUser.getId())
                                    .map(p -> p.getProfilePhotoUrl())
                                    .orElse(null)
                            : null;

                    // Did I review this peer?
                    Optional<Review> myReview = allReviews.stream()
                            .filter(r -> r.getReviewer().getId().equals(currentUser.getId())
                                    && r.getReviewee().getId().equals(m.getUserId()))
                            .findFirst();

                    // Did they review me?
                    Optional<Review> theirReview = allReviews.stream()
                            .filter(r -> r.getReviewer().getId().equals(m.getUserId())
                                    && r.getReviewee().getId().equals(currentUser.getId()))
                            .findFirst();

                    boolean iReviewedThem = myReview.isPresent();
                    boolean theyReviewedMe = theirReview.isPresent();
                    boolean isPublished = myReview.map(Review::getIsPublished).orElse(false)
                            || theirReview.map(Review::getIsPublished).orElse(false);

                    return ReviewWindowDTO.PeerReviewStatus.builder()
                            .peerId(m.getUserId().toString())
                            .peerName(peerName)
                            .profilePhotoUrl(photoUrl)
                            .iReviewedThem(iReviewedThem)
                            .theyReviewedMe(theyReviewedMe)
                            .isPublished(isPublished)
                            .build();
                })
                .collect(Collectors.toList());

        return ReviewWindowDTO.builder()
                .windowOpens(windowOpens)
                .windowCloses(windowCloses)
                .windowOpen(windowOpen)
                .peers(peerStatuses)
                .build();
    }

    public TrustScoreDTO getTrustScore(String userId) {
        // For the MVP, we compute on the fly to ensure it is always perfectly synced 
        // with KYC, Profile, and Review changes without needing complex event listeners.
        recomputeTrustScore(userId);
        TrustScore ts = trustScoreRepository.findByUserId(UUID.fromString(userId)).orElseThrow();
        return modelMapper.map(ts, TrustScoreDTO.class);
    }
    
    public List<ReviewDTO> getPublishedReviews(String userId) {
        List<Review> reviews = reviewRepository.findByRevieweeIdAndIsPublishedTrue(UUID.fromString(userId));
        return reviews.stream().map(this::convertToDto).collect(Collectors.toList());
    }
    
    public List<ReviewDTO> getTripPeerReviews(String tripId) {
        List<Review> reviews = reviewRepository.findByTripId(UUID.fromString(tripId));
        return reviews.stream()
                .filter(Review::getIsPublished)
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    @Transactional
    public void recomputeTrustScore(String userId) {
        User user = userRepository.findById(UUID.fromString(userId)).orElseThrow();
        TrustScore ts = trustScoreRepository.findByUserId(UUID.fromString(userId))
                .orElseGet(() -> {
                    TrustScore newTs = new TrustScore();
                    newTs.setUser(user);
                    return newTs;
                });

        int baseScore = 10;
        
        // Gov ID / KYC Check
        int govIdScore = user.getStatus().name().equals("KYC_VERIFIED") ? 40 : 0;
        
        // Profile Score (dummy check for bio/photo - assume yes for now if not null)
        int profileScore = 10; // For MVP, assume +10 for registering 
        
        // Review Score (Max 40)
        List<Review> reviews = reviewRepository.findByRevieweeIdAndIsPublishedTrue(UUID.fromString(userId));
        int reviewScore = 0;
        
        if (reviews.isEmpty()) {
            // New User Grace
            reviewScore = 20;
        } else {
            double totalStars = 0;
            for (Review r : reviews) {
                double avg = (r.getPunctualityRating() + r.getCleanlinessRating() + 
                              r.getCommunicationRating() + r.getVibeRating() + r.getSafetyRating()) / 5.0;
                totalStars += avg;
            }
            double globalAvg = totalStars / reviews.size();
            reviewScore = (int) Math.round((globalAvg / 5.0) * 40.0);
        }
        
        ts.setBaseScore(baseScore);
        ts.setGovIdScore(govIdScore);
        ts.setProfileScore(profileScore);
        ts.setReviewScore(reviewScore);
        
        ts.setTotalScore(baseScore + govIdScore + profileScore + reviewScore);
        ts.setLastComputedAt(LocalDateTime.now());
        
        trustScoreRepository.save(ts);
    }
    
    private ReviewDTO convertToDto(Review r) {
        ReviewDTO dto = modelMapper.map(r, ReviewDTO.class);
        dto.setTripId(r.getTrip().getId().toString());
        dto.setReviewerId(r.getReviewer().getId().toString());
        dto.setReviewerFirstName(r.getReviewer().getFirstName());
        dto.setReviewerLastName(r.getReviewer().getLastName());
        dto.setRevieweeId(r.getReviewee().getId().toString());
        return dto;
    }
}
