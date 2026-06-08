package com.travyn.trip.repository;

import com.travyn.trip.entity.TripWaypoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TripWaypointRepository extends JpaRepository<TripWaypoint, UUID> {
    List<TripWaypoint> findByTripIdOrderByCreatedAtAsc(UUID tripId);
}
