package com.travyn.safety.repository;

import com.travyn.safety.entity.SafetyCheck;
import com.travyn.safety.entity.SafetyCheckStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface SafetyCheckRepository extends JpaRepository<SafetyCheck, UUID> {
    List<SafetyCheck> findByUserIdAndStatus(UUID userId, SafetyCheckStatus status);
    List<SafetyCheck> findByStatusAndExpiresAtBefore(SafetyCheckStatus status, Instant time);
}
