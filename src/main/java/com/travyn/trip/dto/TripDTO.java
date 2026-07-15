package com.travyn.trip.dto;

import com.travyn.trip.entity.ApprovalMode;
import com.travyn.trip.entity.TripStatus;
import com.travyn.trip.entity.TripType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripDTO {

    private UUID id;
    private UUID creatorId;
    private String creatorName;
    private String title;
    private String destination;
    private String description;
    private LocalDate startDate;
    private LocalDate endDate;
    private int maxSize;
    private TripType tripType;
    private TripStatus status;
    private int trustScoreMin;
    private boolean womenOnly;
    private ApprovalMode approvalMode;
    private String tags;
    private String tripCode;
    private String coverImageUrl;
    private BigDecimal minBudget;
    private BigDecimal maxBudget;
    private int memberCount;
    private int availableSpots;
    private Instant createdAt;
    private Instant updatedAt;

    // Added for /my-trips — the current user's role and status in this trip
    private String memberRole;
    private String memberStatus;

    // Added for chat integration in messages
    private long unreadChatCount;
    private String latestMessageContent;
    private com.travyn.chat.entity.MessageType latestMessageType;
    private Instant latestMessageAt;
}
