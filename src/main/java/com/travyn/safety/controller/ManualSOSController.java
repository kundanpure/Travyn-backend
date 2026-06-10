package com.travyn.safety.controller;

import com.travyn.auth.entity.User;
import com.travyn.auth.service.AuthService;
import com.travyn.common.exception.ResourceNotFoundException;
import com.travyn.common.service.EmailService;
import com.travyn.common.service.TelegramBotService;
import com.travyn.safety.entity.EmergencyContact;
import com.travyn.safety.entity.SOSToken;
import com.travyn.safety.entity.UserLocationHistory;
import com.travyn.safety.repository.EmergencyContactRepository;
import com.travyn.safety.repository.SOSTokenRepository;
import com.travyn.safety.repository.UserLocationHistoryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.security.core.context.SecurityContextHolder;
import com.travyn.auth.repository.UserRepository;

@RestController
@RequestMapping("/api/v1/safety")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Safety Actions", description = "Endpoints for triggering manual safety features")
@Slf4j
public class ManualSOSController {

    private final UserRepository userRepository;
    private final SOSTokenRepository sosTokenRepository;
    private final EmergencyContactRepository emergencyContactRepository;
    private final UserLocationHistoryRepository locationHistoryRepository;
    private final TelegramBotService telegramBotService;
    private final EmailService emailService;

    @Value("${app.base-url:http://localhost:3000}")
    private String frontendUrl;

    @PostMapping("/panic")
    @Operation(summary = "Manually trigger an emergency SOS")
    public ResponseEntity<Map<String, String>> triggerManualSOS() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        // Generate a secure random token (Hex to avoid Telegram Markdown mangling like underscores)
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : randomBytes) {
            sb.append(String.format("%02x", b));
        }
        String tokenStr = sb.toString();

        // Find last known location
        Optional<UserLocationHistory> lastLocation = locationHistoryRepository
                .findFirstByUserIdOrderByRecordedAtDesc(user.getId());
        
        Double lat = null;
        Double lng = null;
        if (lastLocation.isPresent()) {
            lat = lastLocation.get().getLatitude();
            lng = lastLocation.get().getLongitude();
        }

        // Save token
        SOSToken sosToken = SOSToken.builder()
                .token(tokenStr)
                .userId(user.getId())
                .isActive(true)
                .build();
        sosTokenRepository.save(sosToken);

        // Construct the tracking URL
        String trackingUrl = frontendUrl + "/share/sos/" + tokenStr;
        String travelerName = user.getFirstName() + " " + user.getLastName();

        // Notify contacts
        List<EmergencyContact> contacts = emergencyContactRepository.findByUserId(user.getId());
        for (EmergencyContact contact : contacts) {
            // Send via Telegram if connected
            if (contact.getTelegramChatId() != null) {
                telegramBotService.sendSOSAlert(
                        contact.getTelegramChatId(),
                        travelerName,
                        lat,
                        lng,
                        trackingUrl
                );
            }
            
            // Also send via Email as a backup
            emailService.sendSosEmail(
                    contact.getEmail(),
                    contact.getName(),
                    travelerName,
                    trackingUrl,
                    lat,
                    lng
            );
        }

        log.warn("🚨 MANUAL SOS TRIGGERED BY USER: {}", user.getEmail());

        return ResponseEntity.ok(Map.of("message", "SOS triggered successfully", "token", tokenStr));
    }
}
