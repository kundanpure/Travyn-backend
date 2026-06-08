package com.travyn.trip.controller;

import com.travyn.auth.entity.User;
import com.travyn.auth.repository.UserRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@Slf4j
public class MapWebSocketController {

    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Data
    public static class LocationUpdate {
        private Double latitude;
        private Double longitude;
    }

    @Data
    @Builder
    public static class BroadcastLocation {
        private UUID userId;
        private String userName;
        private String initials;
        private Double latitude;
        private Double longitude;
        private Instant timestamp;
    }

    @MessageMapping("/map/{tripId}/location")
    public void handleLocationUpdate(
            @DestinationVariable String tripId,
            @Payload LocationUpdate update,
            SimpMessageHeaderAccessor headerAccessor) {
            
        Principal principal = headerAccessor.getUser();
        if (principal == null) return;
        
        User user = userRepository.findByEmail(principal.getName()).orElse(null);
        if (user == null) return;

        BroadcastLocation broadcast = BroadcastLocation.builder()
                .userId(user.getId())
                .userName(user.getFirstName() + " " + user.getLastName())
                .initials(("" + user.getFirstName().charAt(0) + user.getLastName().charAt(0)).toUpperCase())
                .latitude(update.getLatitude())
                .longitude(update.getLongitude())
                .timestamp(Instant.now())
                .build();

        messagingTemplate.convertAndSend("/topic/map/" + tripId.toLowerCase() + "/locations", broadcast);
    }
}
