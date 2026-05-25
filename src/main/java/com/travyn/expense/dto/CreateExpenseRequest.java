package com.travyn.expense.dto;

import com.travyn.expense.entity.ExpenseCategory;
import com.travyn.expense.entity.SplitType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
public class CreateExpenseRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must be at most 200 characters")
    private String title;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    private ExpenseCategory category;

    private SplitType splitType;

    @NotNull(message = "Date is required")
    private LocalDate date;

    private String notes;

    /**
     * List of user IDs to split with. If empty, splits among all approved trip members.
     */
    private List<UUID> splitWith;

    /**
     * Custom split amounts keyed by userId. Only used when splitType is CUSTOM.
     */
    private Map<UUID, BigDecimal> customAmounts;
}
