package com.travyn.trip.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class TripWaypointDTO {
    private UUID id;
    private UUID tripId;
    private Double latitude;
    private Double longitude;
    private String label;
    private UUID creatorId;
    private String creatorName;
    private String creatorInitials;
    private Instant createdAt;
}
