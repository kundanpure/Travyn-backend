package com.travyn.trip.dto;

import com.travyn.trip.entity.MemberRole;
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
public class TripMemberDTO {

    private UUID userId;
    private String firstName;
    private String lastName;
    private MemberRole role;
    private MemberStatus status;
    private Instant joinedAt;
}
