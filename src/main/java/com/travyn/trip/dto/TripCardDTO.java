package com.travyn.trip.dto;

import com.travyn.trip.entity.TripType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripCardDTO {

    private UUID id;
    private String title;
    private String destination;
    private String coverImageUrl;
    private LocalDate startDate;
    private LocalDate endDate;
    private TripType tripType;
    private int spotsLeft;
    private int maxSize;
    private int memberCount;
    private String creatorName;
    private boolean womenOnly;
    private String tags;
    private BigDecimal minBudget;
    private BigDecimal maxBudget;
}
