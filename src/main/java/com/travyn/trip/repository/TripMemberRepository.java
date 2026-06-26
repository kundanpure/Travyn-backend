package com.travyn.trip.repository;

import com.travyn.trip.entity.MemberStatus;
import com.travyn.trip.entity.TripMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TripMemberRepository extends JpaRepository<TripMember, UUID> {

    List<TripMember> findByTripId(UUID tripId);

    List<TripMember> findByUserId(UUID userId);

    Optional<TripMember> findByTripIdAndUserId(UUID tripId, UUID userId);

    int countByTripIdAndMemberStatus(UUID tripId, MemberStatus memberStatus);

    List<TripMember> findByTripIdAndMemberStatus(UUID tripId, MemberStatus memberStatus);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(t1) > 0 FROM TripMember t1 JOIN TripMember t2 ON t1.tripId = t2.tripId " +
           "JOIN Trip tr ON t1.tripId = tr.id " +
           "WHERE t1.userId = :userA AND t2.userId = :userB " +
           "AND t1.memberStatus = 'APPROVED' AND t2.memberStatus = 'APPROVED' " +
           "AND tr.endDate >= :cutoffDate")
    boolean hasActiveSharedTrip(@org.springframework.data.repository.query.Param("userA") UUID userA, 
                                @org.springframework.data.repository.query.Param("userB") UUID userB, 
                                @org.springframework.data.repository.query.Param("cutoffDate") java.time.LocalDate cutoffDate);

    @org.springframework.data.jpa.repository.Query(
            "SELECT COUNT(m) > 0 FROM TripMember m JOIN Trip t ON m.tripId = t.id " +
            "WHERE m.userId = :userId AND m.memberStatus = 'APPROVED' " +
            "AND t.startDate <= CURRENT_DATE AND t.endDate >= CURRENT_DATE " +
            "AND t.status IN ('IN_PROGRESS', 'OPEN', 'FULL')")
    boolean isUserInOngoingTrip(@org.springframework.data.repository.query.Param("userId") UUID userId);
}
