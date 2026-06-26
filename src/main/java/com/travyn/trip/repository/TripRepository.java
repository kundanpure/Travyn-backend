package com.travyn.trip.repository;

import com.travyn.trip.entity.Trip;
import com.travyn.trip.entity.TripStatus;
import com.travyn.trip.entity.TripType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
            "AND (:destination IS NULL OR LOWER(t.destination) LIKE LOWER(CONCAT('%', CAST(:destination AS string), '%')) OR LOWER(t.title) LIKE LOWER(CONCAT('%', CAST(:destination AS string), '%'))) " +
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

    @Query("SELECT DISTINCT u FROM Trip t " +
           "LEFT JOIN TripMember m ON t.id = m.tripId AND m.memberStatus = 'APPROVED' " +
           "JOIN com.travyn.auth.entity.User u ON (u.id = t.creatorId OR u.id = m.userId) " +
           "WHERE LOWER(t.destination) = LOWER(:destination) " +
           "AND t.status IN ('CONFIRMED', 'ONGOING') " +
           "AND t.startDate <= CURRENT_DATE AND t.endDate >= CURRENT_DATE")
    List<com.travyn.auth.entity.User> findUsersCurrentlyInDestination(@Param("destination") String destination);

    @Modifying
    @Query("UPDATE Trip t SET t.status = com.travyn.trip.entity.TripStatus.IN_PROGRESS " +
           "WHERE t.startDate <= :today AND t.endDate >= :today " +
           "AND t.status IN (com.travyn.trip.entity.TripStatus.OPEN, com.travyn.trip.entity.TripStatus.FULL)")
    int startTrips(@Param("today") LocalDate today);

    @Modifying
    @Query("UPDATE Trip t SET t.status = com.travyn.trip.entity.TripStatus.COMPLETED " +
           "WHERE t.endDate < :today " +
           "AND t.status IN (com.travyn.trip.entity.TripStatus.OPEN, com.travyn.trip.entity.TripStatus.FULL, com.travyn.trip.entity.TripStatus.IN_PROGRESS)")
    int completeTrips(@Param("today") LocalDate today);

    @Query("SELECT COUNT(t) > 0 FROM Trip t " +
           "LEFT JOIN TripMember m ON t.id = m.tripId AND m.memberStatus = 'APPROVED' " +
           "WHERE (t.creatorId = :userId OR m.userId = :userId) " +
           "AND t.status NOT IN (com.travyn.trip.entity.TripStatus.CANCELLED, com.travyn.trip.entity.TripStatus.COMPLETED) " +
           "AND t.startDate <= :newEndDate " +
           "AND t.endDate >= :newStartDate " +
           "AND (:excludeTripId IS NULL OR t.id != :excludeTripId)")
    boolean hasOverlappingTrips(@Param("userId") UUID userId, 
                                @Param("newStartDate") LocalDate newStartDate, 
                                @Param("newEndDate") LocalDate newEndDate,
                                @Param("excludeTripId") UUID excludeTripId);
}
