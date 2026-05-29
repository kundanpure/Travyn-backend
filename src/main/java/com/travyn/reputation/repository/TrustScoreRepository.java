package com.travyn.reputation.repository;

import com.travyn.reputation.entity.TrustScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TrustScoreRepository extends JpaRepository<TrustScore, String> {
    Optional<TrustScore> findByUserId(UUID userId);
}
