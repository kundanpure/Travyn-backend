package com.travyn.chat.repository;

import com.travyn.chat.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    Page<ChatMessage> findByTripIdOrderByCreatedAtDesc(UUID tripId, Pageable pageable);

    long countByTripIdAndCreatedAtAfter(UUID tripId, Instant after);

    long countByTripId(UUID tripId);
}
