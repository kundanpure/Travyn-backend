package com.travyn.notification.dto;

import com.travyn.notification.entity.NotificationType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class NotificationDTO {
    private UUID id;
    private String message;
    private NotificationType type;
    private UUID referenceId;
    private boolean isRead;
    private Instant createdAt;
}
