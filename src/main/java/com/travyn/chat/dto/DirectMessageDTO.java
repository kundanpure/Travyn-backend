package com.travyn.chat.dto;

import com.travyn.chat.entity.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectMessageDTO {
    private UUID id;
    private UUID senderId;
    private UUID receiverId;
    private String senderName;
    private String senderInitials;
    private String content;
    private MessageType messageType;
    private boolean isRead;
    private Instant createdAt;
}
