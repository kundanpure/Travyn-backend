package com.travyn.chat.controller;

import com.travyn.auth.entity.User;
import com.travyn.auth.repository.UserRepository;
import com.travyn.chat.dto.ChatMessageDTO;
import com.travyn.chat.service.ChatService;
import com.travyn.common.exception.UserNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/trips/{tripId}/chat")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "Trip group chat")
public class ChatController {

    private final ChatService chatService;
    private final UserRepository userRepository;

    @GetMapping("/messages")
    @Operation(summary = "Get paginated message history for a trip")
    public ResponseEntity<List<ChatMessageDTO>> getMessages(
            @AuthenticationPrincipal Object principal,
            @PathVariable UUID tripId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        User user = findUserFromPrincipal(principal);
        return ResponseEntity.ok(chatService.getMessages(user.getId(), tripId, page, size));
    }

    @PostMapping("/messages")
    @Operation(summary = "Send a new message to the trip chat")
    public ResponseEntity<ChatMessageDTO> sendMessage(
            @PathVariable UUID tripId,
            @RequestBody com.travyn.chat.dto.SendMessageRequest request,
            @AuthenticationPrincipal Object principal) {
        User user = findUserFromPrincipal(principal);
        return ResponseEntity.ok(chatService.sendMessage(user.getId(), tripId, request));
    }

    @PostMapping("/read")
    @Operation(summary = "Mark trip chat as read up to the current time")
    public ResponseEntity<Void> markAsRead(
            @PathVariable UUID tripId,
            @AuthenticationPrincipal Object principal) {
        User user = findUserFromPrincipal(principal);
        chatService.markChatAsRead(user.getId(), tripId);
        return ResponseEntity.ok().build();
    }

    private User findUserFromPrincipal(Object principal) {
        String email;
        if (principal instanceof UserDetails ud) {
            email = ud.getUsername();
        } else if (principal instanceof String s) {
            email = s;
        } else {
            throw new UserNotFoundException("User not authenticated");
        }
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }
}
