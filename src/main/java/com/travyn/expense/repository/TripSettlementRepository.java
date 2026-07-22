package com.travyn.expense.repository;

import com.travyn.expense.entity.SettlementStatus;
import com.travyn.expense.entity.TripSettlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TripSettlementRepository extends JpaRepository<TripSettlement, UUID> {

    List<TripSettlement> findByTripId(UUID tripId);

    List<TripSettlement> findByTripIdAndStatus(UUID tripId, SettlementStatus status);

    List<TripSettlement> findByTripIdAndToUserIdAndStatus(UUID tripId, UUID toUserId, SettlementStatus status);
}
