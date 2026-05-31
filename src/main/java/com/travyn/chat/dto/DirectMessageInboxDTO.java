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
public class DirectMessageInboxDTO {
    private UUID partnerId;
    private String partnerName;
    private String partnerInitials;
    private String latestMessageContent;
    private MessageType latestMessageType;
    private Instant latestMessageAt;
    private int unreadCount;
}
