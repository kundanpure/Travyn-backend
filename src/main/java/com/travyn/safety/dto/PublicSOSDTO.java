package com.travyn.safety.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicSOSDTO {
    private String userName;
    private String userPhone;
    private String tripName;
    private String tripDestination;
    private Double lastLat;
    private Double lastLng;
    private Instant lastLocationTime;
    private Integer batteryLevel; // Future proofing
    private Instant sosTriggeredAt;
    private boolean isActive;
}
