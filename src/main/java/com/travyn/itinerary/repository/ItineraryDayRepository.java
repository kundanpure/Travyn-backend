package com.travyn.itinerary.repository;

import com.travyn.itinerary.entity.ItineraryDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ItineraryDayRepository extends JpaRepository<ItineraryDay, UUID> {

    List<ItineraryDay> findByTripIdOrderByDateAsc(UUID tripId);

    Optional<ItineraryDay> findByTripIdAndDate(UUID tripId, LocalDate date);

    int countByTripId(UUID tripId);

    void deleteByTripIdAndId(UUID tripId, UUID id);
}
