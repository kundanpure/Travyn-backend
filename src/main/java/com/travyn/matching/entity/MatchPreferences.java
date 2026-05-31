package com.travyn.matching.entity;

import com.travyn.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "match_preferences")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchPreferences {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "smoking_habit", length = 20)
    private SmokingHabit smokingHabit;

    @Enumerated(EnumType.STRING)
    @Column(name = "drinking_habit", length = 20)
    private DrinkingHabit drinkingHabit;

    @Enumerated(EnumType.STRING)
    @Column(name = "trip_pace", length = 20)
    private TripPace tripPace;

    @Enumerated(EnumType.STRING)
    @Column(name = "accommodation_style", length = 20)
    private AccommodationStyle accommodationStyle;

    @Enumerated(EnumType.STRING)
    @Column(name = "planning_style", length = 20)
    private PlanningStyle planningStyle;

    @Enumerated(EnumType.STRING)
    @Column(name = "cleanliness", length = 20)
    private Cleanliness cleanliness;

    @Enumerated(EnumType.STRING)
    @Column(name = "social_energy", length = 20)
    private SocialEnergy socialEnergy;

    /** Comma-separated list of TravelMotivation */
    @Column(name = "travel_motivations", length = 200)
    private String travelMotivations;

    /** Comma-separated list of TravelMeaning */
    @Column(name = "travel_meanings", length = 200)
    private String travelMeanings;

    @Enumerated(EnumType.STRING)
    @Column(name = "trip_experience", length = 20)
    private TripExperience tripExperience;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
