package com.travyn.expense.dto;

import com.travyn.expense.entity.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CreateSettlementRequest {

    @NotNull(message = "Recipient user ID is required")
    private UUID toUserId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    private PaymentMethod paymentMethod = PaymentMethod.CASH;

    private String notes;

    private boolean isDirectReceipt = false;
}
