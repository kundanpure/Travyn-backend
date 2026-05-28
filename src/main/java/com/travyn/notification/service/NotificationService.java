package com.travyn.notification.service;

import com.travyn.notification.dto.NotificationDTO;
import com.travyn.notification.entity.Notification;
import com.travyn.notification.entity.NotificationType;
import com.travyn.notification.repository.NotificationRepository;
import com.travyn.trip.entity.MemberStatus;
import com.travyn.trip.repository.TripMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final TripMemberRepository tripMemberRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional(readOnly = true)
    public List<NotificationDTO> getMyNotifications(UUID userId, int page, int size) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
                .getContent()
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public int getUnreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markAsRead(UUID notificationId, UUID userId) {
        notificationRepository.findById(notificationId).ifPresent(notification -> {
            if (notification.getUserId().equals(userId)) {
                notification.setRead(true);
                notificationRepository.save(notification);
            }
        });
    }

    @Transactional
    public void markAllAsRead(UUID userId) {
        notificationRepository.markAllAsReadByUserId(userId);
    }

    @Transactional
    public void notifyTripMembers(UUID tripId, UUID senderId, String message, NotificationType type, UUID referenceId) {
        // Find all approved members of the trip
        List<UUID> memberIds = tripMemberRepository.findByTripId(tripId).stream()
                .filter(m -> m.getMemberStatus() == MemberStatus.APPROVED && !m.getUserId().equals(senderId))
                .map(m -> m.getUserId())
                .toList();

        for (UUID memberId : memberIds) {
            Notification notification = Notification.builder()
                    .userId(memberId)
                    .message(message)
                    .type(type)
                    .referenceId(referenceId)
                    .isRead(false)
                    .build();

            notification = notificationRepository.save(notification);

            // Broadcast to the user's specific notification topic
            NotificationDTO dto = mapToDTO(notification);
            messagingTemplate.convertAndSend("/topic/user." + memberId + ".notifications", dto);
        }
        
        log.debug("Sent {} notification to {} members for trip {}", type, memberIds.size(), tripId);
    }

    @Transactional
    public void notifyUser(UUID userId, String message, NotificationType type, UUID referenceId) {
        Notification notification = Notification.builder()
                .userId(userId)
                .message(message)
                .type(type)
                .referenceId(referenceId)
                .isRead(false)
                .build();

        notification = notificationRepository.save(notification);

        NotificationDTO dto = mapToDTO(notification);
        messagingTemplate.convertAndSend("/topic/user." + userId + ".notifications", dto);

        log.debug("Sent {} notification to user {}", type, userId);
    }

    private NotificationDTO mapToDTO(Notification notification) {
        return NotificationDTO.builder()
                .id(notification.getId())
                .message(notification.getMessage())
                .type(notification.getType())
                .referenceId(notification.getReferenceId())
                .isRead(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
