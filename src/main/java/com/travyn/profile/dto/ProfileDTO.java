package com.travyn.profile.dto;

import com.travyn.profile.entity.FoodPreference;
import com.travyn.profile.entity.SleepSchedule;
import com.travyn.profile.entity.TravelStyle;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileDTO {

    private UUID userId;
    private String firstName;
    private String lastName;
    private String bio;
    private TravelStyle travelStyle;
    private Integer budgetMin;
    private Integer budgetMax;
    private SleepSchedule sleepSchedule;
    private Integer personalityScale;
    private FoodPreference foodPreference;
    private String languages;
    private boolean remoteWorker;
    private String profilePhotoUrl;
    private String coverPhotoUrl;
    private int profileCompleteness;
}
