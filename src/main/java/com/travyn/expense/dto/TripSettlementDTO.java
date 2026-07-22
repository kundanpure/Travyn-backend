package com.travyn.expense.dto;

import com.travyn.expense.entity.PaymentMethod;
import com.travyn.expense.entity.SettlementStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripSettlementDTO {

    private UUID id;
    private UUID tripId;
    private UUID fromUserId;
    private String fromUserName;
    private UUID toUserId;
    private String toUserName;
    private BigDecimal amount;
    private PaymentMethod paymentMethod;
    private SettlementStatus status;
    private String notes;
    private String rejectionReason;
    private Instant createdAt;
    private Instant updatedAt;
}
