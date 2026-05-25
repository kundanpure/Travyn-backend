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
}
