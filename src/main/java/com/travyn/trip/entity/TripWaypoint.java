package com.travyn.trip.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trip_waypoints")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripWaypoint {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tripId;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(nullable = false)
    private UUID creatorId;

    @Column(nullable = false, updatable = false)
    @CreationTimestamp
    private Instant createdAt;
}
