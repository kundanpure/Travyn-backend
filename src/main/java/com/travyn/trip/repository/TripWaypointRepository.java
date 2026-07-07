package com.travyn.trip.repository;

import com.travyn.trip.entity.TripWaypoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TripWaypointRepository extends JpaRepository<TripWaypoint, UUID> {
    List<TripWaypoint> findByTripIdOrderByCreatedAtAsc(UUID tripId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM TripWaypoint t WHERE t.tripId = :tripId")
    void deleteByTripId(@org.springframework.data.repository.query.Param("tripId") UUID tripId);
}
