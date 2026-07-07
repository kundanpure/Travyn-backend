package com.travyn.trip.repository;

import com.travyn.trip.entity.TripCancellationVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

@Repository
public interface TripCancellationVoteRepository extends JpaRepository<TripCancellationVote, UUID> {
    List<TripCancellationVote> findByTripId(UUID tripId);
    
    Optional<TripCancellationVote> findByTripIdAndUserId(UUID tripId, UUID userId);

    @Modifying
    @Query("DELETE FROM TripCancellationVote v WHERE v.trip.id = :tripId")
    void deleteByTripId(UUID tripId);
}
