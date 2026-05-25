package com.travyn.chat.controller;

import com.travyn.auth.entity.User;
import com.travyn.auth.repository.UserRepository;
import com.travyn.chat.dto.ChatMessageDTO;
import com.travyn.chat.dto.SendMessageRequest;
import com.travyn.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketController {

    private final ChatService chatService;
    private final UserRepository userRepository;

    /**
     * Receives messages from clients at /app/chat/{tripId}
     * and persists + broadcasts to /topic/chat/{tripId}
     */
    @MessageMapping("/chat/{tripId}")
    public void handleMessage(
            @DestinationVariable UUID tripId,
            @Payload SendMessageRequest request,
            SimpMessageHeaderAccessor headerAccessor) {

        Principal principal = headerAccessor.getUser();
        if (principal == null) {
            log.warn("WebSocket message received without authenticated principal for trip {}", tripId);
            return;
        }

        String email = principal.getName();
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            log.warn("WebSocket message from unknown email: {}", email);
            return;
        }

        chatService.sendMessage(user.getId(), tripId, request);
    }
}
