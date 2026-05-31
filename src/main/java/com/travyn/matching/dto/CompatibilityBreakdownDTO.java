package com.travyn.matching.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompatibilityBreakdownDTO {
    private int travelStyleScore;
    private int budgetScore;
    private int sleepScore;
    private int foodScore;
    private int smokingDrinkingScore;
    private int tripPaceScore;
    private int accommodationScore;
    private int planningScore;
    private int travelMotivationScore;
    private int travelMeaningScore;
    private int tripExperienceScore;
    private int socialEnergyScore;
    
    // Overall weighted score (0-100)
    private int overallScore;
}
