package com.travyn.safety.dto;

import com.travyn.safety.entity.SafetyCheckStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class SafetyCheckDTO {
    private UUID id;
    private UUID tripId;
    private SafetyCheckStatus status;
    private Instant createdAt;
    private Instant expiresAt;
}
