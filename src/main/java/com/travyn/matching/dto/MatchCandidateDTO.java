package com.travyn.matching.dto;

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
public class MatchCandidateDTO {
    private UUID userId;
    private String firstName;
    private String lastName;
    private String username;
    private Integer age;
    private String profilePhotoUrl;
    
    private int trustScore;
    private List<String> travelStyles;
    private Integer budgetMin;
    private Integer budgetMax;
    private String personalityLabel; // Derived from profile
    
    private int compatibilityScore;
    private CompatibilityBreakdownDTO breakdown;
}
