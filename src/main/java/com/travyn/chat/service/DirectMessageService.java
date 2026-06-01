package com.travyn.chat.service;

import com.travyn.auth.entity.User;
import com.travyn.auth.repository.UserRepository;
import com.travyn.chat.dto.DirectMessageDTO;
import com.travyn.chat.dto.DirectMessageInboxDTO;
import com.travyn.chat.dto.SendDirectMessageRequest;
import com.travyn.chat.entity.DirectMessage;
import com.travyn.chat.entity.MessageType;
import com.travyn.chat.repository.DirectMessageRepository;
import com.travyn.matching.repository.MatchConnectionRepository;
import com.travyn.notification.entity.NotificationType;
import com.travyn.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DirectMessageService {

    private final DirectMessageRepository dmRepository;
    private final UserRepository userRepository;
    private final MatchConnectionRepository matchConnectionRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationService notificationService;
    private final com.travyn.trip.repository.TripMemberRepository tripMemberRepository;

    @Transactional(readOnly = true)
    public List<DirectMessageDTO> getMessages(UUID userId, UUID partnerId, int page, int size) {
        // Validate messaging permission
        validateMessagingPermission(userId, partnerId);

        Page<DirectMessage> messagePage = dmRepository.findConversationHistory(userId, partnerId, PageRequest.of(page, size));
        
        List<DirectMessage> messages = new ArrayList<>(messagePage.getContent());
        Collections.reverse(messages); // Oldest first for display

        Set<UUID> userIds = new HashSet<>(Arrays.asList(userId, partnerId));
        Map<UUID, User> usersById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return messages.stream().map(msg -> mapToDTO(msg, usersById)).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DirectMessageInboxDTO> getInbox(UUID userId) {
        // Find latest message per conversation
        List<DirectMessage> latestMessages = dmRepository.findLatestMessagesForUser(userId);
        
        // Find all unread messages for user
        List<DirectMessage> unreadMessages = dmRepository.findUnreadMessagesForUser(userId);
        Map<UUID, Long> unreadCountByPartner = unreadMessages.stream()
                .collect(Collectors.groupingBy(DirectMessage::getSenderId, Collectors.counting()));

        // Also fetch all mutual match IDs so we can show empty conversations too
        List<UUID> mutualIds = matchConnectionRepository.findMutualMatchUserIds(userId);
        
        // Collect partner IDs
        Set<UUID> partnerIds = new HashSet<>(mutualIds);
        latestMessages.forEach(msg -> {
            partnerIds.add(msg.getSenderId().equals(userId) ? msg.getReceiverId() : msg.getSenderId());
        });
        
        Map<UUID, User> partnersById = userRepository.findAllById(partnerIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<DirectMessageInboxDTO> inbox = new ArrayList<>();
        
        for (UUID partnerId : partnerIds) {
            User partner = partnersById.get(partnerId);
            if (partner == null) continue;
            
            // Find latest message for this partner
            DirectMessage latestMsg = latestMessages.stream()
                    .filter(m -> m.getSenderId().equals(partnerId) || m.getReceiverId().equals(partnerId))
                    .findFirst().orElse(null);
                    
            int unreadCount = unreadCountByPartner.getOrDefault(partnerId, 0L).intValue();
            
            inbox.add(DirectMessageInboxDTO.builder()
                    .partnerId(partnerId)
                    .partnerName(partner.getFirstName() + " " + partner.getLastName())
                    .partnerInitials(("" + partner.getFirstName().charAt(0) + partner.getLastName().charAt(0)).toUpperCase())
                    .latestMessageContent(latestMsg != null ? latestMsg.getContent() : "")
                    .latestMessageType(latestMsg != null ? latestMsg.getMessageType() : MessageType.TEXT)
                    .latestMessageAt(latestMsg != null ? latestMsg.getCreatedAt() : null)
                    .unreadCount(unreadCount)
                    .build());
        }
        
        // Sort by latest message date descending
        inbox.sort((a, b) -> {
            if (a.getLatestMessageAt() == null && b.getLatestMessageAt() == null) return 0;
            if (a.getLatestMessageAt() == null) return 1;
            if (b.getLatestMessageAt() == null) return -1;
            return b.getLatestMessageAt().compareTo(a.getLatestMessageAt());
        });
        
        return inbox;
    }

    @Transactional
    public DirectMessageDTO sendMessage(UUID senderId, UUID receiverId, SendDirectMessageRequest request) {
        validateMessagingPermission(senderId, receiverId);

        DirectMessage message = DirectMessage.builder()
                .senderId(senderId)
                .receiverId(receiverId)
                .content(request.getContent().trim())
                .messageType(request.getMessageType() != null ? request.getMessageType() : MessageType.TEXT)
                .isRead(false)
                .build();

        message = dmRepository.save(message);

        User sender = userRepository.findById(senderId).orElseThrow();
        Map<UUID, User> usersById = new HashMap<>();
        usersById.put(senderId, sender);
        
        DirectMessageDTO dto = mapToDTO(message, usersById);

        // Send real-time message via STOMP using direct topic based on UUID
        messagingTemplate.convertAndSend(
                "/topic/user." + receiverId + ".dm.messages",
                dto
        );
        
        // Also send it to the sender's queue so their other open tabs can sync
        messagingTemplate.convertAndSend(
                "/topic/user." + senderId + ".dm.messages",
                dto
        );

        // Generate in-app notification
        String notificationMsg = "New message from " + sender.getFirstName();
        notificationService.notifyUser(
                receiverId,
                notificationMsg,
                NotificationType.DIRECT_MESSAGE,
                senderId
        );

        return dto;
    }

    @Transactional
    public void markMessagesAsRead(UUID userId, UUID partnerId) {
        List<DirectMessage> unread = dmRepository.findConversationHistory(partnerId, userId, PageRequest.of(0, 100))
                .getContent().stream()
                .filter(m -> m.getReceiverId().equals(userId) && !m.isRead())
                .collect(Collectors.toList());
                
        if (!unread.isEmpty()) {
            unread.forEach(m -> m.setRead(true));
            dmRepository.saveAll(unread);
            
            // Notify partner that messages were read
            messagingTemplate.convertAndSend(
                    "/topic/user." + partnerId + ".dm.read-receipts",
                    userId.toString() // partner knows that userId read their messages
            );
        }
    }

    @Transactional(readOnly = true)
    public Map<String, String> getConnectionStatus(UUID userId, UUID partnerId) {
        if (matchConnectionRepository.findMutualMatchUserIds(userId).contains(partnerId)) {
            return Map.of("status", "MUTUAL");
        }
        
        if (matchConnectionRepository.hasPassed(userId, partnerId) || matchConnectionRepository.hasPassed(partnerId, userId)) {
            return Map.of("status", "REJECTED");
        }

        List<DirectMessage> recentHistory = dmRepository.findConversationHistory(userId, partnerId, PageRequest.of(0, 10)).getContent();
        boolean iSent = recentHistory.stream().anyMatch(msg -> msg.getSenderId().equals(userId));
        boolean theySent = recentHistory.stream().anyMatch(msg -> msg.getSenderId().equals(partnerId));
        
        if (iSent && theySent) {
            return Map.of("status", "MUTUAL");
        } else if (iSent && !theySent) {
            return Map.of("status", "PENDING_SENT");
        } else if (theySent && !iSent) {
            return Map.of("status", "PENDING_RECEIVED");
        }
        
        java.time.LocalDate cutoffDate = java.time.LocalDate.now().minusDays(14);
        if (tripMemberRepository.hasActiveSharedTrip(userId, partnerId, cutoffDate)) {
            return Map.of("status", "CO_TRAVELER");
        }

        return Map.of("status", "NONE");
    }

    private DirectMessageDTO mapToDTO(DirectMessage msg, Map<UUID, User> usersById) {
        User sender = usersById.get(msg.getSenderId());
        String name = "Unknown";
        String initials = "??";
        if (sender != null) {
            name = sender.getFirstName() + " " + sender.getLastName();
            initials = ("" + sender.getFirstName().charAt(0) + sender.getLastName().charAt(0)).toUpperCase();
        }

        return DirectMessageDTO.builder()
                .id(msg.getId())
                .senderId(msg.getSenderId())
                .receiverId(msg.getReceiverId())
                .senderName(name)
                .senderInitials(initials)
                .content(msg.getContent())
                .messageType(msg.getMessageType())
                .isRead(msg.isRead())
                .createdAt(msg.getCreatedAt())
                .build();
    }

    private void validateMessagingPermission(UUID userId, UUID partnerId) {
        // 1. Check if Mutual Matches (Permanent Connection)
        List<UUID> mutualIds = matchConnectionRepository.findMutualMatchUserIds(userId);
        if (mutualIds.contains(partnerId)) {
            return; // ALLOWED
        }

        // 2. Check if Receiver rejected Sender
        if (matchConnectionRepository.hasPassed(partnerId, userId)) {
            throw new IllegalArgumentException("You can no longer message this user.");
        }

        // 3. Check if they are Co-Travelers in an active trip
        java.time.LocalDate cutoffDate = java.time.LocalDate.now().minusDays(14);
        if (tripMemberRepository.hasActiveSharedTrip(userId, partnerId, cutoffDate)) {
            // Check 1-Message Limit Spam Filter
            // Find if sender has already sent an unread message and receiver hasn't replied
            List<DirectMessage> recentHistory = dmRepository.findConversationHistory(userId, partnerId, PageRequest.of(0, 10)).getContent();
            boolean hasPendingMessage = recentHistory.stream()
                    .anyMatch(msg -> msg.getSenderId().equals(userId) && !msg.isRead());
            boolean receiverHasReplied = recentHistory.stream()
                    .anyMatch(msg -> msg.getSenderId().equals(partnerId));
            
            if (hasPendingMessage && !receiverHasReplied) {
                throw new IllegalArgumentException("Request sent. You can send more messages once they accept.");
            }
            
            return; // ALLOWED (First Request)
        }

        // 4. Blocked
        throw new IllegalArgumentException("You can only message mutual matches or co-travelers.");
    }
}
