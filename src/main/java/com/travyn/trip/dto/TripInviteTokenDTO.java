package com.travyn.trip.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class TripInviteTokenDTO {
    private UUID id;
    private String token;
    private String link;
    private int maxUses;
    private int usedCount;
    private boolean autoApprove;
    private boolean isActive;
    private Instant expiresAt;
    private Instant createdAt;
}
