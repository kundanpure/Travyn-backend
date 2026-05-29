package com.travyn.reputation.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ReviewDTO {
    private String id;
    private String tripId;
    private String reviewerId;
    private String reviewerFirstName;
    private String reviewerLastName;
    private String revieweeId;
    private Integer punctualityRating;
    private Integer cleanlinessRating;
    private Integer communicationRating;
    private Integer vibeRating;
    private Integer safetyRating;
    private String textReview;
    private Boolean isPublished;
    private LocalDateTime createdAt;
}
