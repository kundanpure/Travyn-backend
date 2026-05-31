package com.travyn.matching.repository;

import com.travyn.matching.entity.MatchPreferences;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MatchPreferencesRepository extends JpaRepository<MatchPreferences, UUID> {
    Optional<MatchPreferences> findByUserId(UUID userId);
}
