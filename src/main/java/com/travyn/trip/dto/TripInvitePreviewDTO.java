package com.travyn.trip.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class TripInvitePreviewDTO {
    private UUID tripId;
    private String tripTitle;
    private String destination;
    private String description;
    private LocalDate startDate;
    private LocalDate endDate;
    private String tripType;
    private String creatorName;
    private String creatorProfilePhoto;
    private boolean creatorVerified;
    private int memberCount;
    private int maxSize;
    private String coverImageUrl;
    private boolean womenOnly;
    private boolean isFull;
    private boolean isExpired;
    private String invitedByName;
}
