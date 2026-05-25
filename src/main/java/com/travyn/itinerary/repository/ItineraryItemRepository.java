package com.travyn.itinerary.repository;

import com.travyn.itinerary.entity.ItineraryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ItineraryItemRepository extends JpaRepository<ItineraryItem, UUID> {

    List<ItineraryItem> findByDayIdOrderBySortOrderAsc(UUID dayId);

    List<ItineraryItem> findByTripId(UUID tripId);

    int countByDayId(UUID dayId);

    void deleteByDayId(UUID dayId);
}
