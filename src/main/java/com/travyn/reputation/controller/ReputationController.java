package com.travyn.reputation.controller;

import com.travyn.reputation.dto.ReviewDTO;
import com.travyn.reputation.dto.ReviewRequest;
import com.travyn.reputation.dto.ReviewWindowDTO;
import com.travyn.reputation.dto.TrustScoreDTO;
import com.travyn.reputation.service.ReputationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReputationController {

    private final ReputationService reputationService;

    @PostMapping("/trips/{tripId}/reviews/{revieweeId}")
    public ResponseEntity<ReviewDTO> submitReview(
            @AuthenticationPrincipal String reviewerEmail,
            @PathVariable String tripId,
            @PathVariable String revieweeId,
            @Valid @RequestBody ReviewRequest request) {
        
        ReviewDTO review = reputationService.submitReview(reviewerEmail, tripId, revieweeId, request);
        return ResponseEntity.ok(review);
    }

    @GetMapping("/trips/{tripId}/review-window")
    public ResponseEntity<ReviewWindowDTO> getReviewWindow(
            @AuthenticationPrincipal String userEmail,
            @PathVariable String tripId) {
        ReviewWindowDTO windowStatus = reputationService.getReviewWindowStatus(tripId, userEmail);
        return ResponseEntity.ok(windowStatus);
    }

    @GetMapping("/trips/{tripId}/peer-reviews")
    public ResponseEntity<List<ReviewDTO>> getTripPeerReviews(@PathVariable String tripId) {
        return ResponseEntity.ok(reputationService.getTripPeerReviews(tripId));
    }

    @GetMapping("/users/{userId}/trust-score")
    public ResponseEntity<TrustScoreDTO> getTrustScore(@PathVariable String userId) {
        return ResponseEntity.ok(reputationService.getTrustScore(userId));
    }

    @GetMapping("/users/{userId}/reviews")
    public ResponseEntity<List<ReviewDTO>> getPublishedReviews(@PathVariable String userId) {
        return ResponseEntity.ok(reputationService.getPublishedReviews(userId));
    }
}
