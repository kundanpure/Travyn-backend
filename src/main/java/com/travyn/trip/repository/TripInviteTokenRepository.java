package com.travyn.trip.repository;

import com.travyn.trip.entity.TripInviteToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripInviteTokenRepository extends JpaRepository<TripInviteToken, UUID> {

    Optional<TripInviteToken> findByTokenAndIsActiveTrue(String token);

    List<TripInviteToken> findByTripIdAndIsActiveTrueOrderByCreatedAtDesc(UUID tripId);
}
