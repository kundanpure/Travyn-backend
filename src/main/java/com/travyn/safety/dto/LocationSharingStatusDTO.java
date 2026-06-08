package com.travyn.safety.dto;

import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationSharingStatusDTO {
    private UUID tripId;
    private String tripTitle;
    private boolean isActive;
    private Double accommodationLat;
    private Double accommodationLng;
    private String accommodationLabel;
    private Double lastLatitude;
    private Double lastLongitude;
    private String lastRecordedAt;
}
