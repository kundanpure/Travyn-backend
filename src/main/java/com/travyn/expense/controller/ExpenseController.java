package com.travyn.expense.controller;

import com.travyn.auth.entity.User;
import com.travyn.auth.repository.UserRepository;
import com.travyn.common.exception.UserNotFoundException;
import com.travyn.expense.dto.*;
import com.travyn.expense.service.ExpenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/trips/{tripId}/expenses")
@RequiredArgsConstructor
@Tag(name = "Expenses", description = "Trip expense splitting and settlements")
public class ExpenseController {

    private final ExpenseService expenseService;
    private final UserRepository userRepository;

    @GetMapping
    @Operation(summary = "List all expenses for a trip")
    public ResponseEntity<List<ExpenseDTO>> getExpenses(
            @AuthenticationPrincipal String email,
            @PathVariable UUID tripId) {
        User user = findUserByEmail(email);
        return ResponseEntity.ok(expenseService.getExpenses(user.getId(), tripId));
    }

    @PostMapping
    @Operation(summary = "Add a new expense to a trip")
    public ResponseEntity<ExpenseDTO> addExpense(
            @AuthenticationPrincipal String email,
            @PathVariable UUID tripId,
            @Valid @RequestBody CreateExpenseRequest request) {
        User user = findUserByEmail(email);
        ExpenseDTO expense = expenseService.addExpense(user.getId(), tripId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(expense);
    }

    @DeleteMapping("/{expenseId}")
    @Operation(summary = "Delete an expense (payer or trip creator only)")
    public ResponseEntity<Void> deleteExpense(
            @AuthenticationPrincipal String email,
            @PathVariable UUID tripId,
            @PathVariable UUID expenseId) {
        User user = findUserByEmail(email);
        expenseService.deleteExpense(user.getId(), tripId, expenseId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/summary")
    @Operation(summary = "Get trip expense summary with per-member balances")
    public ResponseEntity<ExpenseSummaryDTO> getSummary(
            @AuthenticationPrincipal String email,
            @PathVariable UUID tripId) {
        User user = findUserByEmail(email);
        return ResponseEntity.ok(expenseService.getSummary(user.getId(), tripId));
    }

    @GetMapping("/settlements")
    @Operation(summary = "Get optimized settlement plan (who pays whom)")
    public ResponseEntity<List<SettlementDTO>> getSettlements(
            @AuthenticationPrincipal String email,
            @PathVariable UUID tripId) {
        User user = findUserByEmail(email);
        return ResponseEntity.ok(expenseService.getSettlements(user.getId(), tripId));
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }
}
