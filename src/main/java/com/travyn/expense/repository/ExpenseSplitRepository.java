package com.travyn.expense.repository;

import com.travyn.expense.entity.ExpenseSplit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ExpenseSplitRepository extends JpaRepository<ExpenseSplit, UUID> {

    List<ExpenseSplit> findByExpenseId(UUID expenseId);

    List<ExpenseSplit> findByExpenseIdIn(List<UUID> expenseIds);

    @Modifying
    @Query("DELETE FROM ExpenseSplit e WHERE e.expenseId = :expenseId")
    void deleteByExpenseId(@Param("expenseId") UUID expenseId);
}
