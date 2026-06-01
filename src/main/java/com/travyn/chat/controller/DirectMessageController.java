package com.travyn.chat.controller;

import com.travyn.auth.entity.User;
import com.travyn.auth.repository.UserRepository;
import com.travyn.chat.dto.DirectMessageDTO;
import com.travyn.chat.dto.DirectMessageInboxDTO;
import com.travyn.chat.dto.SendDirectMessageRequest;
import com.travyn.chat.service.DirectMessageService;
import com.travyn.common.exception.UserNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dm")
@RequiredArgsConstructor
@Tag(name = "Direct Messages", description = "1-on-1 Chat API")
public class DirectMessageController {

    private final DirectMessageService dmService;
    private final UserRepository userRepository;

    @GetMapping("/inbox")
    @Operation(summary = "Get list of mutual matches with their latest message and unread counts")
    public ResponseEntity<List<DirectMessageInboxDTO>> getInbox(
            @AuthenticationPrincipal String email) {
        User user = findUserByEmail(email);
        return ResponseEntity.ok(dmService.getInbox(user.getId()));
    }

    @GetMapping("/{partnerId}/messages")
    @Operation(summary = "Get paginated 1-on-1 message history")
    public ResponseEntity<List<DirectMessageDTO>> getMessages(
            @PathVariable UUID partnerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal String email) {
        User user = findUserByEmail(email);
        return ResponseEntity.ok(dmService.getMessages(user.getId(), partnerId, page, size));
    }

    @PostMapping("/{partnerId}/messages")
    @Operation(summary = "Send a new 1-on-1 direct message")
    public ResponseEntity<DirectMessageDTO> sendMessage(
            @PathVariable UUID partnerId,
            @RequestBody SendDirectMessageRequest request,
            @AuthenticationPrincipal String email) {
        User user = findUserByEmail(email);
        return ResponseEntity.ok(dmService.sendMessage(user.getId(), partnerId, request));
    }

    @PostMapping("/{partnerId}/read")
    @Operation(summary = "Mark messages from a partner as read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable UUID partnerId,
            @AuthenticationPrincipal String email) {
        User user = findUserByEmail(email);
        dmService.markMessagesAsRead(user.getId(), partnerId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{partnerId}/status")
    @Operation(summary = "Get the connection/messaging status with a partner")
    public ResponseEntity<java.util.Map<String, String>> getConnectionStatus(
            @PathVariable UUID partnerId,
            @AuthenticationPrincipal String email) {
        User user = findUserByEmail(email);
        return ResponseEntity.ok(dmService.getConnectionStatus(user.getId(), partnerId));
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }
}
