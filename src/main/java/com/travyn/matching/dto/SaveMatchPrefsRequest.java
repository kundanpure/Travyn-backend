package com.travyn.matching.dto;

import com.travyn.matching.entity.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveMatchPrefsRequest {
    
    @NotNull(message = "Smoking habit is required")
    private SmokingHabit smokingHabit;
    
    @NotNull(message = "Drinking habit is required")
    private DrinkingHabit drinkingHabit;
    
    @NotNull(message = "Trip pace is required")
    private TripPace tripPace;
    
    @NotNull(message = "Accommodation style is required")
    private AccommodationStyle accommodationStyle;
    
    @NotNull(message = "Planning style is required")
    private PlanningStyle planningStyle;
    
    @NotNull(message = "Cleanliness is required")
    private Cleanliness cleanliness;
    
    @NotNull(message = "Social energy is required")
    private SocialEnergy socialEnergy;
    
    @NotNull(message = "Travel motivations are required")
    private List<TravelMotivation> travelMotivations;
    
    @NotNull(message = "Travel meanings are required")
    private List<TravelMeaning> travelMeanings;
    
    @NotNull(message = "Trip experience is required")
    private TripExperience tripExperience;
}
