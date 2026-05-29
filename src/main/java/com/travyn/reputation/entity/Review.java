package com.travyn.reputation.entity;

import com.travyn.auth.entity.User;
import com.travyn.trip.entity.Trip;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reviews", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"trip_id", "reviewer_id", "reviewee_id"})
})
@Getter
@Setter
public class Review {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewee_id", nullable = false)
    private User reviewee;

    @Column(nullable = false)
    private Integer punctualityRating;

    @Column(nullable = false)
    private Integer cleanlinessRating;

    @Column(nullable = false)
    private Integer communicationRating;

    @Column(nullable = false)
    private Integer vibeRating;

    @Column(nullable = false)
    private Integer safetyRating;

    @Column(length = 500)
    private String textReview;

    @Column(nullable = false)
    private Boolean isPublished = false;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
