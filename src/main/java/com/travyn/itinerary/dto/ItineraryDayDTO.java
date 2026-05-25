package com.travyn.itinerary.dto;

import com.travyn.itinerary.entity.ItemCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItineraryDayDTO {

    private UUID id;
    private UUID tripId;
    private LocalDate date;
    private String title;
    private String notes;
    private int dayNumber;
    private List<ItineraryItemDTO> items;
    private Instant createdAt;
    private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItineraryItemDTO {
        private UUID id;
        private UUID dayId;
        private String title;
        private String description;
        private String location;
        private LocalTime startTime;
        private LocalTime endTime;
        private ItemCategory category;
        private int sortOrder;
        private UUID createdBy;
        private String createdByName;
        private Instant createdAt;
    }
}
