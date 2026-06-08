package com.travyn.safety.repository;

import com.travyn.safety.entity.TripLocationSharing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TripLocationSharingRepository extends JpaRepository<TripLocationSharing, UUID> {
    Optional<TripLocationSharing> findByUserIdAndTripId(UUID userId, UUID tripId);
    List<TripLocationSharing> findByIsActiveTrue();
    List<TripLocationSharing> findByUserId(UUID userId);
}
