package com.travyn.trip.repository;

import com.travyn.trip.entity.Trip;
import com.travyn.trip.entity.TripStatus;
import com.travyn.trip.entity.TripType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface TripRepository extends JpaRepository<Trip, UUID> {

    List<Trip> findByCreatorId(UUID creatorId);

    List<Trip> findByStatus(TripStatus status);

    @Query("SELECT t FROM Trip t WHERE (:status IS NULL OR t.status IN :status) " +
            "AND (:isUpcoming = false OR t.startDate > current_date) " +
            "AND (:isOngoing = false OR (t.startDate <= current_date AND t.endDate >= current_date)) " +
            "AND (:destination IS NULL OR LOWER(t.destination) LIKE LOWER(CONCAT('%', CAST(:destination AS string), '%'))) " +
            "AND (:tripType IS NULL OR t.tripType = :tripType) " +
            "AND (:fromDate IS NULL OR t.startDate >= :fromDate) " +
            "AND (:toDate IS NULL OR t.endDate <= :toDate) " +
            "AND (:isVerifiedWoman = true OR t.womenOnly = false)")
    Page<Trip> discoverTrips(
            @Param("status") List<TripStatus> status,
            @Param("destination") String destination,
            @Param("tripType") TripType tripType,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("isVerifiedWoman") boolean isVerifiedWoman,
            @Param("isUpcoming") boolean isUpcoming,
            @Param("isOngoing") boolean isOngoing,
            Pageable pageable);

    @Query("SELECT COUNT(t) > 0 FROM Trip t LEFT JOIN TripMember m ON t.id = m.tripId " +
           "WHERE LOWER(t.destination) = LOWER(:destination) " +
           "AND (t.creatorId = :userId OR (m.userId = :userId AND m.memberStatus = 'APPROVED'))")
    boolean hasApprovedTripToDestination(@Param("destination") String destination, @Param("userId") UUID userId);
}
