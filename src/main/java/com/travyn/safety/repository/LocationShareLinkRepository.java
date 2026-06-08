package com.travyn.safety.repository;

import com.travyn.safety.entity.LocationShareLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LocationShareLinkRepository extends JpaRepository<LocationShareLink, UUID> {
    Optional<LocationShareLink> findByToken(String token);
    List<LocationShareLink> findByUserIdAndTripId(UUID userId, UUID tripId);
}
