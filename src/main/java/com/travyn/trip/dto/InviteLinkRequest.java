package com.travyn.trip.dto;

import lombok.Data;

@Data
public class InviteLinkRequest {
    private int maxUses = 0;          // 0 = unlimited
    private int expiresInDays = 7;    // Default 7 days
    private boolean autoApprove = true;
}
