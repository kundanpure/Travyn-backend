package com.travyn.chat.repository;

import com.travyn.chat.entity.DirectMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, UUID> {

    @Query("SELECT m FROM DirectMessage m WHERE (m.senderId = :user1 AND m.receiverId = :user2) OR (m.senderId = :user2 AND m.receiverId = :user1) ORDER BY m.createdAt DESC")
    Page<DirectMessage> findConversationHistory(@Param("user1") UUID user1, @Param("user2") UUID user2, Pageable pageable);

    @Query("SELECT m FROM DirectMessage m WHERE m.receiverId = :userId AND m.isRead = false")
    List<DirectMessage> findUnreadMessagesForUser(@Param("userId") UUID userId);
    
    @Query(value = "SELECT * FROM ( " +
                   "  SELECT m.*, ROW_NUMBER() OVER (PARTITION BY " +
                   "    CASE WHEN sender_id < receiver_id THEN sender_id ELSE receiver_id END, " +
                   "    CASE WHEN sender_id < receiver_id THEN receiver_id ELSE sender_id END " +
                   "    ORDER BY created_at DESC) as rn " +
                   "  FROM direct_messages m " +
                   "  WHERE m.sender_id = :userId OR m.receiver_id = :userId " +
                   ") sub WHERE rn = 1 ORDER BY created_at DESC", nativeQuery = true)
    List<DirectMessage> findLatestMessagesForUser(@Param("userId") UUID userId);
}
