package com.travyn.trip.dto;

import com.travyn.trip.entity.MemberStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HandleJoinRequest {

    @NotNull(message = "Status is required")
    private MemberStatus status;
}
