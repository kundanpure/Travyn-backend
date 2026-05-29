package com.travyn.reputation.repository;

import com.travyn.reputation.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, String> {
    Optional<Review> findByTripIdAndReviewerIdAndRevieweeId(UUID tripId, UUID reviewerId, UUID revieweeId);
    
    List<Review> findByTripIdAndRevieweeId(UUID tripId, UUID revieweeId);
    
    List<Review> findByTripIdAndReviewerId(UUID tripId, UUID reviewerId);
    
    List<Review> findByRevieweeIdAndIsPublishedTrue(UUID revieweeId);
}
