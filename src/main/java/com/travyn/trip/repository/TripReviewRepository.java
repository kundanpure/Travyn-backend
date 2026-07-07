package com.travyn.trip.repository;

import com.travyn.trip.entity.TripReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TripReviewRepository extends JpaRepository<TripReview, UUID> {
    List<TripReview> findByTripIdOrderByCreatedAtDesc(UUID tripId);
    Optional<TripReview> findByTripIdAndReviewerId(UUID tripId, UUID reviewerId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM TripReview t WHERE t.trip.id = :tripId")
    void deleteByTripId(@org.springframework.data.repository.query.Param("tripId") UUID tripId);
}
