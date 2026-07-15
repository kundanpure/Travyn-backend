package com.travyn.destination.controller;

import com.travyn.destination.dto.DestinationInsightDTO;
import com.travyn.destination.dto.DestinationInsightRequest;
import com.travyn.destination.service.DestinationInsightService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/destinations")
@RequiredArgsConstructor
public class DestinationInsightController {

    private final DestinationInsightService insightService;

    @GetMapping("/{destination}/insights")
    public ResponseEntity<List<DestinationInsightDTO>> getInsights(
            @AuthenticationPrincipal String userEmail,
            @PathVariable String destination) {
        return ResponseEntity.ok(insightService.getInsightsForDestination(destination, userEmail));
    }

    @PostMapping("/{destination}/insights")
    public ResponseEntity<DestinationInsightDTO> postInsight(
            @AuthenticationPrincipal String userEmail,
            @PathVariable String destination,
            @Valid @RequestBody DestinationInsightRequest request) {
        return ResponseEntity.ok(insightService.postInsight(userEmail, destination, request));
    }

    @PutMapping("/insights/{insightId}")
    public ResponseEntity<DestinationInsightDTO> editInsight(
            @AuthenticationPrincipal String userEmail,
            @PathVariable String insightId,
            @Valid @RequestBody DestinationInsightRequest request) {
        return ResponseEntity.ok(insightService.editInsight(insightId, userEmail, request));
    }

    @PostMapping("/insights/{insightId}/upvote")
    public ResponseEntity<Void> upvoteInsight(
            @AuthenticationPrincipal String userEmail,
            @PathVariable String insightId) {
        insightService.upvoteInsight(insightId, userEmail);
        return ResponseEntity.ok().build();
    }
}
