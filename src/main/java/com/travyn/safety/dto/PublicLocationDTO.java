package com.travyn.safety.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicLocationDTO {
    private String travelerName;
    private Double latitude;
    private Double longitude;
    private String lastUpdated;
    private String tripTitle;
    private String tripDestination;
    private boolean expired;
    private String message;
}
