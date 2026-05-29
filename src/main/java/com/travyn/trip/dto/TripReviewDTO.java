package com.travyn.trip.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class TripReviewDTO {
    private UUID id;
    private UUID tripId;
    private UUID reviewerId;
    private String reviewerName;
    private String reviewerAvatarUrl;
    private Integer rating;
    private String textReview;
    private LocalDateTime createdAt;
}
