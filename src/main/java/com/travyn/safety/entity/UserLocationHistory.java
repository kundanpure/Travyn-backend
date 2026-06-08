package com.travyn.safety.entity;

import com.travyn.safety.crypto.AesEncryptor;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_location_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLocationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Column(name = "encrypted_latitude", nullable = false, length = 255)
    @Convert(converter = AesEncryptor.class)
    private Double latitude;

    @Column(name = "encrypted_longitude", nullable = false, length = 255)
    @Convert(converter = AesEncryptor.class)
    private Double longitude;

    @Column(nullable = false)
    private Float accuracy;

    @Column(name = "recorded_at", nullable = false)
    @Builder.Default
    private Instant recordedAt = Instant.now();
}
