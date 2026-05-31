package com.travyn.matching.dto;

import com.travyn.matching.entity.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchPreferencesDTO {
    private UUID userId;
    private SmokingHabit smokingHabit;
    private DrinkingHabit drinkingHabit;
    private TripPace tripPace;
    private AccommodationStyle accommodationStyle;
    private PlanningStyle planningStyle;
    private Cleanliness cleanliness;
    private SocialEnergy socialEnergy;
    private List<TravelMotivation> travelMotivations;
    private List<TravelMeaning> travelMeanings;
    private TripExperience tripExperience;
}
