package com.travyn.trip.dto;

import com.travyn.trip.entity.ApprovalMode;
import com.travyn.trip.entity.TripType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTripRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;

    @NotBlank(message = "Destination is required")
    @Size(max = 200, message = "Destination must not exceed 200 characters")
    private String destination;

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;

    @NotNull(message = "Start date is required")
    @FutureOrPresent(message = "Start date must be in the future or today")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    @Future(message = "End date must be in the future")
    private LocalDate endDate;

    @Min(value = 2, message = "Trip must allow at least 2 members")
    @Max(value = 12, message = "Trip must not exceed 12 members")
    private int maxSize;

    @NotNull(message = "Trip type is required")
    private TripType tripType;

    @Min(value = 0, message = "Trust score minimum must be at least 0")
    private int trustScoreMin;

    private boolean womenOnly;

    private ApprovalMode approvalMode;

    @Size(max = 500, message = "Tags must not exceed 500 characters")
    private String tags;

    @Size(max = 500, message = "Cover image URL must not exceed 500 characters")
    private String coverImageUrl;

    @Min(value = 0, message = "Minimum budget must be at least 0")
    private BigDecimal minBudget;

    @Min(value = 0, message = "Maximum budget must be at least 0")
    private BigDecimal maxBudget;
}
