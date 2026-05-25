package com.travyn.trip.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "trips")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 200)
    private String destination;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "max_size", nullable = false)
    private int maxSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "trip_type", nullable = false, length = 30)
    private TripType tripType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private TripStatus status = TripStatus.OPEN;

    @Column(name = "trust_score_min", nullable = false)
    @Builder.Default
    private int trustScoreMin = 0;

    @Column(name = "women_only", nullable = false)
    @Builder.Default
    private boolean womenOnly = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_mode", nullable = false, length = 20)
    @Builder.Default
    private ApprovalMode approvalMode = ApprovalMode.MANUAL;

    @Column(length = 500)
    private String tags;

    @Column(name = "trip_code", nullable = false, unique = true, length = 8)
    private String tripCode;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
