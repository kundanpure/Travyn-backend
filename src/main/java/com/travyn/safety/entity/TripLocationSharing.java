package com.travyn.safety.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trip_location_sharing",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "trip_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripLocationSharing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = false;

    @Column(name = "accommodation_lat")
    private Double accommodationLat;

    @Column(name = "accommodation_lng")
    private Double accommodationLng;

    @Column(name = "accommodation_label", length = 200)
    private String accommodationLabel;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
