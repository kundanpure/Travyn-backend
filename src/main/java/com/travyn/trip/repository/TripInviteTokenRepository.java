package com.travyn.trip.repository;

import com.travyn.trip.entity.TripInviteToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripInviteTokenRepository extends JpaRepository<TripInviteToken, UUID> {

    Optional<TripInviteToken> findByTokenAndIsActiveTrue(String token);

    List<TripInviteToken> findByTripIdAndIsActiveTrueOrderByCreatedAtDesc(UUID tripId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM TripInviteToken t WHERE t.tripId = :tripId")
    void deleteByTripId(@org.springframework.data.repository.query.Param("tripId") UUID tripId);
}
