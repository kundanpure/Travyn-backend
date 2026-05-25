package com.travyn.trip.dto;

import com.travyn.trip.entity.MemberStatus;
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
public class JoinRequestDTO {

    private UUID memberId;
    private UUID userId;
    private String firstName;
    private String lastName;
    private MemberStatus status;
    private Instant requestedAt;
}
