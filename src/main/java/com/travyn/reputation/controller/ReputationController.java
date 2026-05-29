package com.travyn.reputation.controller;

import com.travyn.reputation.dto.ReviewDTO;
import com.travyn.reputation.dto.ReviewRequest;
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

    @GetMapping("/users/{userId}/trust-score")
    public ResponseEntity<TrustScoreDTO> getTrustScore(@PathVariable String userId) {
        return ResponseEntity.ok(reputationService.getTrustScore(userId));
    }

    @GetMapping("/users/{userId}/reviews")
    public ResponseEntity<List<ReviewDTO>> getPublishedReviews(@PathVariable String userId) {
        return ResponseEntity.ok(reputationService.getPublishedReviews(userId));
    }
}
