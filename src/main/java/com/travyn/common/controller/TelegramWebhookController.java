package com.travyn.common.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.travyn.common.service.TelegramBotService;
import com.travyn.safety.entity.EmergencyContact;
import com.travyn.safety.repository.EmergencyContactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/telegram")
@RequiredArgsConstructor
@Slf4j
public class TelegramWebhookController {

    private final EmergencyContactRepository contactRepository;
    private final TelegramBotService telegramBotService;

    @Value("${telegram.webhook.secret:}")
    private String webhookSecret;

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secretToken,
            @RequestBody JsonNode payload) {
        try {
            // Verify the request came from Telegram (if secret is configured)
            if (webhookSecret != null && !webhookSecret.isBlank()
                    && !webhookSecret.equals(secretToken)) {
                log.warn("Telegram webhook rejected: invalid secret token");
                return ResponseEntity.status(401).body("Unauthorized");
            }
            if (payload.has("message")) {
                JsonNode message = payload.get("message");
                if (message.has("text") && message.has("chat")) {
                    String text = message.get("text").asText();
                    Long chatId = message.get("chat").get("id").asLong();

                    if (text.startsWith("/start ")) {
                        String contactIdStr = text.substring(7).trim();
                        try {
                            UUID contactId = UUID.fromString(contactIdStr);
                            Optional<EmergencyContact> contactOpt = contactRepository.findById(contactId);

                            if (contactOpt.isPresent()) {
                                EmergencyContact contact = contactOpt.get();
                                contact.setTelegramChatId(chatId);
                                contactRepository.save(contact);

                                telegramBotService.sendMessage(chatId, 
                                    "✅ *Successfully Connected!*\n\n" +
                                    "You are now registered as an emergency contact for " + contact.getName() + " (via Travyn).\n\n" +
                                    "You will receive instant SOS alerts here if they trigger an emergency."
                                );
                                log.info("Linked telegram chat ID {} to contact {}", chatId, contactId);
                            } else {
                                telegramBotService.sendMessage(chatId, "❌ Invalid or expired setup link.");
                            }
                        } catch (IllegalArgumentException e) {
                            telegramBotService.sendMessage(chatId, "❌ Invalid setup link format.");
                        }
                    }
                }
            }
            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            log.error("Error processing telegram webhook", e);
            return ResponseEntity.ok("Error"); // Telegram retries if we don't send 200 OK
        }
    }
}
