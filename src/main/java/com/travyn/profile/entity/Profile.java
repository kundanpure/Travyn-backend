package com.travyn.profile.entity;

import com.travyn.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Profile {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "id")
    private User user;

    @Column(length = 500)
    private String bio;

    @Enumerated(EnumType.STRING)
    @Column(name = "travel_style", length = 30)
    private TravelStyle travelStyle;

    @Column(name = "budget_min")
    private Integer budgetMin;

    @Column(name = "budget_max")
    private Integer budgetMax;

    @Enumerated(EnumType.STRING)
    @Column(name = "sleep_schedule", length = 30)
    private SleepSchedule sleepSchedule;

    @Column(name = "personality_scale")
    private Integer personalityScale;

    @Enumerated(EnumType.STRING)
    @Column(name = "food_preference", length = 30)
    private FoodPreference foodPreference;

    @Column(length = 500)
    private String languages;

    @Column(name = "remote_worker", nullable = false)
    @Builder.Default
    private boolean remoteWorker = false;

    @Column(name = "profile_photo_url", length = 500)
    private String profilePhotoUrl;

    @Column(name = "cover_photo_url", length = 500)
    private String coverPhotoUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
