package com.travyn.safety.repository;

import com.travyn.safety.entity.SOSToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SOSTokenRepository extends JpaRepository<SOSToken, UUID> {
    Optional<SOSToken> findByTokenAndIsActiveTrue(String token);
}
