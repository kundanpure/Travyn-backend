package com.travyn.notification.controller;

import com.travyn.auth.entity.User;
import com.travyn.auth.repository.UserRepository;
import com.travyn.notification.dto.PushSubscriptionRequest;
import com.travyn.notification.service.WebPushService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/push")
@RequiredArgsConstructor
public class WebPushController {

    private final WebPushService webPushService;
    private final UserRepository userRepository;

    @GetMapping("/public-key")
    public ResponseEntity<Map<String, String>> getPublicKey() {
        return ResponseEntity.ok(Map.of("publicKey", webPushService.getPublicKey()));
    }

    @PostMapping("/subscribe")
    public ResponseEntity<Void> subscribe(@AuthenticationPrincipal String email, @RequestBody PushSubscriptionRequest request) {
        User user = userRepository.findByEmail(email).orElseThrow();
        webPushService.subscribe(user.getId(), request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/unsubscribe")
    public ResponseEntity<Void> unsubscribe(@RequestParam String endpoint) {
        webPushService.unsubscribe(endpoint);
        return ResponseEntity.ok().build();
    }
}
