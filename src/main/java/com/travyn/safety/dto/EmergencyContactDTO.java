package com.travyn.safety.dto;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmergencyContactDTO {
    private UUID id;
    private String name;
    private String phoneNumber;
    private String email;
    private String relationship;
    private boolean telegramConnected;
    private Instant createdAt;
}
