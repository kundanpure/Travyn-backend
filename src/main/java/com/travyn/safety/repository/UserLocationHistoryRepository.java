package com.travyn.safety.repository;

import com.travyn.safety.entity.UserLocationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserLocationHistoryRepository extends JpaRepository<UserLocationHistory, UUID> {

    @Query("SELECT h FROM UserLocationHistory h WHERE h.userId = :userId AND h.tripId = :tripId " +
            "ORDER BY h.recordedAt DESC LIMIT 1")
    Optional<UserLocationHistory> findLatestByUserIdAndTripId(
            @Param("userId") UUID userId, @Param("tripId") UUID tripId);

    @Query("SELECT h FROM UserLocationHistory h WHERE h.userId = :userId AND h.tripId = :tripId " +
            "AND h.recordedAt >= :since ORDER BY h.recordedAt ASC")
    List<UserLocationHistory> findRecentByUserIdAndTripId(
            @Param("userId") UUID userId,
            @Param("tripId") UUID tripId,
            @Param("since") Instant since);
}
