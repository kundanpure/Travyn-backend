package com.travyn.chat.service;

import com.travyn.auth.entity.User;
import com.travyn.auth.repository.UserRepository;
import com.travyn.chat.dto.ChatMessageDTO;
import com.travyn.chat.dto.SendMessageRequest;
import com.travyn.chat.entity.ChatMessage;
import com.travyn.chat.entity.MessageType;
import com.travyn.chat.repository.ChatMessageRepository;
import com.travyn.expense.exception.ExpenseAccessDeniedException;
import com.travyn.notification.entity.NotificationType;
import com.travyn.notification.service.NotificationService;
import com.travyn.trip.entity.MemberStatus;
import com.travyn.trip.entity.Trip;
import com.travyn.trip.exception.TripNotFoundException;
import com.travyn.trip.repository.TripMemberRepository;
import com.travyn.trip.repository.TripRepository;
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
public class ChatService {

    private final ChatMessageRepository messageRepository;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<ChatMessageDTO> getMessages(UUID tripId, int page, int size) {
        tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException("Trip not found"));

        Page<ChatMessage> messagePage = messageRepository.findByTripIdOrderByCreatedAtDesc(
                tripId, PageRequest.of(page, size));

        List<ChatMessage> messages = new ArrayList<>(messagePage.getContent());
        Collections.reverse(messages); // Oldest first for display

        Set<UUID> senderIds = messages.stream()
                .map(ChatMessage::getSenderId)
                .collect(Collectors.toSet());
        Map<UUID, User> usersById = userRepository.findAllById(senderIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return messages.stream().map(msg -> mapToDTO(msg, usersById)).toList();
    }

    @Transactional
    public ChatMessageDTO sendMessage(UUID userId, UUID tripId, SendMessageRequest request) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException("Trip not found"));
        validateMembership(userId, tripId);

        ChatMessage message = ChatMessage.builder()
                .tripId(tripId)
                .senderId(userId)
                .content(request.getContent().trim())
                .messageType(request.getMessageType() != null ? request.getMessageType() : MessageType.TEXT)
                .build();

        message = messageRepository.save(message);

        User sender = userRepository.findById(userId).orElse(null);
        ChatMessageDTO dto = mapToDTO(message, sender);

        // Broadcast to subscribers
        messagingTemplate.convertAndSend("/topic/chat/" + tripId, dto);
        log.debug("Chat message sent in trip {} by user {}", tripId, userId);

        // Notify trip members
        String notificationMsg = "New message from " + dto.getSenderName() + " in " + trip.getTitle();
        notificationService.notifyTripMembers(tripId, userId, notificationMsg, NotificationType.CHAT_MESSAGE, tripId);

        return dto;
    }

    @Transactional
    public void sendSystemMessage(UUID tripId, String content) {
        // Use a "system" UUID for system messages
        UUID systemId = UUID.fromString("00000000-0000-0000-0000-000000000000");

        ChatMessage message = ChatMessage.builder()
                .tripId(tripId)
                .senderId(systemId)
                .content(content)
                .messageType(MessageType.SYSTEM)
                .build();

        message = messageRepository.save(message);

        ChatMessageDTO dto = ChatMessageDTO.builder()
                .id(message.getId())
                .tripId(message.getTripId())
                .senderId(systemId)
                .senderName("System")
                .senderInitials("SY")
                .content(message.getContent())
                .messageType(MessageType.SYSTEM)
                .createdAt(message.getCreatedAt())
                .build();

        messagingTemplate.convertAndSend("/topic/chat/" + tripId, dto);
    }

    private ChatMessageDTO mapToDTO(ChatMessage msg, Map<UUID, User> usersById) {
        User sender = usersById.get(msg.getSenderId());
        return mapToDTO(msg, sender);
    }

    private ChatMessageDTO mapToDTO(ChatMessage msg, User sender) {
        String name = "Unknown";
        String initials = "??";
        if (msg.getMessageType() == MessageType.SYSTEM) {
            name = "System";
            initials = "SY";
        } else if (sender != null) {
            name = sender.getFirstName() + " " + sender.getLastName();
            initials = ("" + sender.getFirstName().charAt(0) + sender.getLastName().charAt(0)).toUpperCase();
        }

        return ChatMessageDTO.builder()
                .id(msg.getId())
                .tripId(msg.getTripId())
                .senderId(msg.getSenderId())
                .senderName(name)
                .senderInitials(initials)
                .content(msg.getContent())
                .messageType(msg.getMessageType())
                .createdAt(msg.getCreatedAt())
                .build();
    }

    private void validateMembership(UUID userId, UUID tripId) {
        boolean isMember = tripMemberRepository.findByTripIdAndUserId(tripId, userId)
                .filter(m -> m.getMemberStatus() == MemberStatus.APPROVED)
                .isPresent();

        if (!isMember) {
            throw new ExpenseAccessDeniedException("You must be an approved member of this trip");
        }
    }
}
