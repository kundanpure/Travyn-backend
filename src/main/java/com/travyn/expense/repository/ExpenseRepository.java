package com.travyn.expense.repository;

import com.travyn.expense.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    List<Expense> findByTripIdOrderByDateDesc(UUID tripId);

    int countByTripId(UUID tripId);
}
