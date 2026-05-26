package com.travyn.profile.dto;

import com.travyn.auth.entity.Gender;
import com.travyn.profile.entity.FoodPreference;
import com.travyn.profile.entity.SleepSchedule;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {

    @Size(max = 500, message = "Bio must not exceed 500 characters")
    private String bio;

    /** List of TravelStyle enum names (e.g. ["ADVENTURE", "CULTURAL"]) */
    private List<String> travelStyles;

    @Min(value = 0, message = "Budget minimum must be at least 0")
    private Integer budgetMin;

    @Min(value = 0, message = "Budget maximum must be at least 0")
    private Integer budgetMax;

    private SleepSchedule sleepSchedule;

    @Min(value = 1, message = "Personality scale must be between 1 and 10")
    @Max(value = 10, message = "Personality scale must be between 1 and 10")
    private Integer personalityScale;

    private FoodPreference foodPreference;

    @Size(max = 500, message = "Languages must not exceed 500 characters")
    private String languages;

    private Boolean remoteWorker;

    @Size(max = 500, message = "Profile photo URL must not exceed 500 characters")
    private String profilePhotoUrl;

    @Size(max = 500, message = "Cover photo URL must not exceed 500 characters")
    private String coverPhotoUrl;

    /**
     * Optional gender update — enforced by service to max 2 changes lifetime.
     * Pass null to leave gender unchanged.
     */
    private Gender gender;
}

