package com.travyn.reputation.service;

import com.travyn.auth.entity.User;
import com.travyn.auth.repository.UserRepository;
import com.travyn.reputation.dto.ReviewDTO;
import com.travyn.reputation.dto.ReviewRequest;
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

import java.time.LocalDateTime;
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
    private final ModelMapper modelMapper;

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
        }

        return convertToDto(review);
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
