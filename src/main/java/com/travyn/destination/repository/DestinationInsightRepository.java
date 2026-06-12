package com.travyn.destination.repository;

import com.travyn.destination.entity.DestinationInsight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface DestinationInsightRepository extends JpaRepository<DestinationInsight, UUID> {
    
    @Query("SELECT d FROM DestinationInsight d WHERE LOWER(d.destination) = LOWER(:destination) " +
           "AND (d.category != 'ALERT' OR d.createdAt > :cutoff) " +
           "ORDER BY d.upvotes DESC, d.createdAt DESC")
    List<DestinationInsight> findActiveInsights(@Param("destination") String destination, @Param("cutoff") LocalDateTime cutoff);
}
