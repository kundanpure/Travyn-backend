package com.travyn.matching.repository;

import com.travyn.matching.entity.MatchAction;
import com.travyn.matching.entity.MatchConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MatchConnectionRepository extends JpaRepository<MatchConnection, UUID> {

    Optional<MatchConnection> findByUserIdAndTargetId(UUID userId, UUID targetId);

    // List of UUIDs of users I have already actioned (connected or passed)
    @Query("SELECT mc.target.id FROM MatchConnection mc WHERE mc.user.id = :userId")
    List<UUID> findActionedTargetIds(@Param("userId") UUID userId);

    // Users who have CONNECTED with me
    @Query("SELECT mc.user.id FROM MatchConnection mc WHERE mc.target.id = :userId AND mc.action = 'CONNECT'")
    List<UUID> findUsersWhoConnectedWithMe(@Param("userId") UUID userId);

    // Find mutual matches: I connected with them AND they connected with me
    @Query("SELECT mc1.target.id FROM MatchConnection mc1 " +
           "INNER JOIN MatchConnection mc2 ON mc1.target.id = mc2.user.id " +
           "WHERE mc1.user.id = :userId AND mc2.target.id = :userId " +
           "AND mc1.action = 'CONNECT' AND mc2.action = 'CONNECT'")
    List<UUID> findMutualMatchUserIds(@Param("userId") UUID userId);
}
