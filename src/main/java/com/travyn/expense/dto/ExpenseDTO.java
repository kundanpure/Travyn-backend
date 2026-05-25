package com.travyn.expense.dto;

import com.travyn.expense.entity.ExpenseCategory;
import com.travyn.expense.entity.SplitType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseDTO {

    private UUID id;
    private UUID tripId;
    private UUID paidBy;
    private String paidByName;
    private String title;
    private BigDecimal amount;
    private String currency;
    private ExpenseCategory category;
    private SplitType splitType;
    private LocalDate date;
    private String notes;
    private List<SplitDTO> splits;
    private Instant createdAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SplitDTO {
        private UUID id;
        private UUID userId;
        private String userName;
        private BigDecimal amount;
    }
}
